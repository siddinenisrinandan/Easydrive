package com.driveease.service;

import com.driveease.dto.Dtos.CreateBookingRequest;
import com.driveease.dto.Dtos.PaymentRequest;
import com.driveease.dto.Dtos.ProfileUpdate;
import com.driveease.dto.Dtos.Quote;
import com.driveease.dto.Dtos.QuoteRequest;
import com.driveease.model.*;
import com.driveease.repository.*;
import com.driveease.service.PaymentGatewayService.GatewayResult;
import com.driveease.util.Geo;
import com.driveease.util.PasswordHasher;
import com.driveease.web.ApiException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/** The complete booking life cycle: create -> pay -> start trip (OTP) -> end trip -> review, or cancel with refund. */
@Service
public class BookingService {

    /** Statuses that block the vehicle / driver for the booked time range. */
    public static final Set<BookingStatus> ACTIVE = EnumSet.of(
            BookingStatus.PENDING_PAYMENT, BookingStatus.CONFIRMED, BookingStatus.ONGOING);

    private final BookingRepository bookingRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final PaymentRepository paymentRepository;
    private final SavedPaymentMethodRepository savedRepository;
    private final CustomerRepository customerRepository;
    private final PricingService pricingService;
    private final PaymentGatewayService gateway;
    private final AuthService authService;

    public BookingService(BookingRepository bookingRepository, VehicleRepository vehicleRepository,
                          DriverRepository driverRepository, PaymentRepository paymentRepository,
                          SavedPaymentMethodRepository savedRepository, CustomerRepository customerRepository,
                          PricingService pricingService, PaymentGatewayService gateway, AuthService authService) {
        this.bookingRepository = bookingRepository;
        this.vehicleRepository = vehicleRepository;
        this.driverRepository = driverRepository;
        this.paymentRepository = paymentRepository;
        this.savedRepository = savedRepository;
        this.customerRepository = customerRepository;
        this.pricingService = pricingService;
        this.gateway = gateway;
        this.authService = authService;
    }

    // ------------------------------------------------------------------ availability

    public boolean isVehicleFree(Long vehicleId, LocalDateTime start, LocalDateTime end) {
        return bookingRepository.countVehicleOverlaps(vehicleId, ACTIVE, start, end) == 0;
    }

    // ------------------------------------------------------------------ create

    @Transactional
    public Booking create(Customer customer, CreateBookingRequest req) {
        Vehicle v = vehicleRepository.findById(req.vehicleId() == null ? -1L : req.vehicleId())
                .orElseThrow(() -> ApiException.notFound("Vehicle not found"));
        if (!v.isAvailable()) {
            throw ApiException.conflict(v.getName() + " is under maintenance right now. Please choose another vehicle.");
        }
        if (req.startTime() == null || req.startTime().isBefore(LocalDateTime.now().minusMinutes(10))) {
            throw ApiException.badRequest("Pickup time must be in the future");
        }
        Catalog.Location pickup = Catalog.location(req.pickupLocation())
                .orElseThrow(() -> ApiException.badRequest("Please choose a pickup location"));
        Catalog.Location drop = Catalog.location(
                req.dropLocation() == null || req.dropLocation().isBlank() ? req.pickupLocation() : req.dropLocation())
                .orElseThrow(() -> ApiException.badRequest("Please choose a valid drop location"));

        Quote q = pricingService.quote(v, new QuoteRequest(v.getId(), req.startTime(), req.endTime(),
                req.insurancePlan(), req.withDriver(), req.addons(), req.couponCode(), pickup.name(), drop.name()));

        if (!isVehicleFree(v.getId(), req.startTime(), req.endTime())) {
            throw ApiException.conflict(v.getName() + " is already booked for these dates. Try different dates or another vehicle.");
        }

        // customer KYC details captured at checkout
        authService.updateProfile(customer, new ProfileUpdate(req.fullName(), req.phone(), req.dateOfBirth(),
                req.licenseNumber(), req.licenseExpiry(), req.address(), null,
                req.emergencyContactName(), req.emergencyContactPhone()));
        if (q.withDriver() == false && (customer.getLicenseNumber() == null || customer.getLicenseNumber().isBlank())) {
            throw ApiException.badRequest("A valid driving licence number is required for self-drive rentals");
        }
        if (customer.getLicenseNumber() != null && !customer.getLicenseNumber().isBlank()
                && !customer.getLicenseNumber().replaceAll("[\\s-]", "").matches("[A-Z]{2}[0-9]{2}[0-9A-Z]{9,13}")) {
            throw ApiException.badRequest("Driving licence number looks invalid. Format: TS0920190001234");
        }

        Booking b = new Booking();
        b.setBookingRef(newRef());
        b.setCustomer(customer);
        b.setVehicle(v);
        b.setWithDriver(q.withDriver());
        if (q.withDriver()) {
            b.setDriver(findFreeDriver(req.startTime(), req.endTime()));
        }
        b.setPickupLocation(pickup.name());
        b.setPickupLat(pickup.lat());
        b.setPickupLng(pickup.lng());
        b.setDropLocation(drop.name());
        b.setDropLat(drop.lat());
        b.setDropLng(drop.lng());
        b.setStartTime(req.startTime());
        b.setEndTime(req.endTime());
        b.setInsurancePlan(PricingService.parsePlan(q.insurancePlan()));
        b.setAddons(String.join(",", q.addons()));
        b.setCouponCode(q.couponApplied() ? req.couponCode().trim().toUpperCase() : null);
        b.setSpecialRequests(req.specialRequests());
        applyQuote(b, q);
        b.setStatus(BookingStatus.PENDING_PAYMENT);
        b.setPaymentStatus(PaymentStatus.PENDING);
        b.setTripOtp(PasswordHasher.randomDigits(4));
        b.setShareCode(PasswordHasher.randomToken(9));
        b.setCreatedAt(LocalDateTime.now());
        return bookingRepository.save(b);
    }

    private void applyQuote(Booking b, Quote q) {
        b.setTotalHours(q.totalHours());
        b.setBillableDays(q.billableDays());
        b.setBaseAmount(q.baseAmount());
        b.setWeekendSurcharge(q.weekendSurcharge());
        b.setLongRentalDiscount(q.longRentalDiscount());
        b.setInsuranceAmount(q.insuranceAmount());
        b.setDriverAmount(q.driverAmount());
        b.setAddonsAmount(q.addonsAmount());
        b.setOneWayFee(q.oneWayFee());
        b.setCouponDiscount(q.couponDiscount());
        b.setTaxAmount(q.taxAmount());
        b.setTotalAmount(q.totalAmount());
        b.setDepositAmount(q.depositAmount());
        b.setKmLimit(q.kmLimit());
        b.setExtraKmRate(q.extraKmRate());
        b.setFinalAmount(q.totalAmount());
    }

    private Driver findFreeDriver(LocalDateTime start, LocalDateTime end) {
        for (Driver d : driverRepository.findByAvailableTrueOrderByRatingDesc()) {
            if (bookingRepository.countDriverOverlaps(d.getId(), ACTIVE, start, end) == 0) {
                return d;
            }
        }
        throw ApiException.conflict("All our drivers are busy for these dates. Please choose self-drive or other dates.");
    }

    private String newRef() {
        String ref;
        do {
            ref = "DE" + Long.toString(ThreadLocalRandom.current().nextLong(36L * 36 * 36 * 36 * 36 * 36L, 36L * 36 * 36 * 36 * 36 * 36 * 36L), 36)
                    .toUpperCase();
        } while (bookingRepository.existsByBookingRef(ref));
        return ref;
    }

    // ------------------------------------------------------------------ payment

    /** A declined payment is still recorded (FAILED) for the audit log, so ApiException must not roll back. */
    @Transactional(noRollbackFor = ApiException.class)
    public Booking pay(Customer customer, String ref, PaymentRequest req) {
        Booking b = getOwned(customer, ref);
        if (b.getStatus() != BookingStatus.PENDING_PAYMENT) {
            throw ApiException.conflict("This booking is already " + b.getStatus().name().toLowerCase().replace('_', ' '));
        }
        // re-check availability right before taking money
        if (bookingRepository.countVehicleOverlaps(b.getVehicle().getId(), ACTIVE, b.getStartTime(), b.getEndTime()) > 1) {
            throw ApiException.conflict("Sorry, this vehicle was just booked by someone else for these dates.");
        }
        SavedPaymentMethod saved = null;
        if (req.savedMethodId() != null) {
            saved = savedRepository.findByIdAndCustomerId(req.savedMethodId(), customer.getId())
                    .orElseThrow(() -> ApiException.notFound("Saved payment method not found"));
        }
        GatewayResult r = gateway.charge(req, b.getTotalAmount(), saved);

        Payment p = new Payment();
        p.setBooking(b);
        p.setBookingRef(b.getBookingRef());
        p.setCustomerName(customer.getFullName());
        p.setKind("CHARGE");
        p.setMethod(r.method());
        p.setAmount(b.getTotalAmount());
        p.setTransactionId(r.transactionId());
        p.setMaskedDetails(r.maskedDetails());
        p.setGatewayMessage(r.message());
        p.setCreatedAt(LocalDateTime.now());

        if (!r.success()) {
            p.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(p);
            throw ApiException.paymentFailed(r.message() + ". No money was deducted. Please try another method.");
        }

        b.setPaymentMethod(r.method());
        b.setStatus(BookingStatus.CONFIRMED);
        if (r.method() == PaymentMethodType.PAY_AT_PICKUP) {
            p.setStatus(PaymentStatus.PENDING);
            b.setPaymentStatus(PaymentStatus.PENDING);
        } else {
            p.setStatus(PaymentStatus.SUCCESS);
            b.setPaymentStatus(PaymentStatus.SUCCESS);
            customer.setLoyaltyPoints(customer.getLoyaltyPoints() + (int) (b.getTotalAmount() / 100));
            customerRepository.save(customer);
        }
        paymentRepository.save(p);

        if (saved == null && req.saveMethod()
                && (r.method() == PaymentMethodType.UPI || r.method() == PaymentMethodType.CARD)) {
            saveInstrument(customer, r);
        }
        return bookingRepository.save(b);
    }

    public SavedPaymentMethod saveInstrument(Customer customer, GatewayResult r) {
        SavedPaymentMethod m = new SavedPaymentMethod();
        m.setCustomer(customer);
        m.setType(r.method());
        m.setLabel(r.label());
        m.setMaskedValue(r.maskedDetails());
        m.setBrand(r.brand());
        m.setExpiry(r.expiry());
        m.setHolderName(r.holderName());
        m.setGatewayToken(PaymentGatewayService.newGatewayToken());
        m.setDefaultMethod(savedRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId()).isEmpty());
        m.setCreatedAt(LocalDateTime.now());
        return savedRepository.save(m);
    }

    // ------------------------------------------------------------------ cancel

    /**
     * Cancellation policy:
     * 24h+ before pickup: 100% refund, 4-24h: 50% refund, under 4h: no refund.
     */
    @Transactional
    public Booking cancel(Customer customer, String ref) {
        Booking b = getOwned(customer, ref);
        return cancelInternal(b, false);
    }

    @Transactional
    public Booking cancelInternal(Booking b, boolean byAdmin) {
        if (b.getStatus() != BookingStatus.PENDING_PAYMENT && b.getStatus() != BookingStatus.CONFIRMED) {
            throw ApiException.conflict("Only upcoming bookings can be cancelled");
        }
        double refund = 0;
        if (b.getPaymentStatus() == PaymentStatus.SUCCESS) {
            long hoursBefore = Duration.between(LocalDateTime.now(), b.getStartTime()).toHours();
            double pct = byAdmin || hoursBefore >= 24 ? 1.0 : hoursBefore >= 4 ? 0.5 : 0.0;
            refund = Math.round(b.getTotalAmount() * pct);
            if (refund > 0) {
                Payment p = new Payment();
                p.setBooking(b);
                p.setBookingRef(b.getBookingRef());
                p.setCustomerName(b.getCustomer().getFullName());
                p.setKind("REFUND");
                p.setMethod(b.getPaymentMethod());
                p.setStatus(PaymentStatus.SUCCESS);
                p.setAmount(refund);
                p.setTransactionId("RFD" + System.currentTimeMillis());
                p.setGatewayMessage((int) (pct * 100) + "% refund to original payment method in 3-5 working days");
                p.setCreatedAt(LocalDateTime.now());
                paymentRepository.save(p);
            }
            b.setPaymentStatus(refund >= b.getTotalAmount() ? PaymentStatus.REFUNDED
                    : refund > 0 ? PaymentStatus.PARTIALLY_REFUNDED : PaymentStatus.SUCCESS);
        }
        b.setRefundAmount(refund);
        b.setFinalAmount(Geo.round2(b.getPaymentStatus() == PaymentStatus.PENDING ? 0 : b.getTotalAmount() - refund));
        b.setStatus(BookingStatus.CANCELLED);
        b.setCancelledAt(LocalDateTime.now());
        return bookingRepository.save(b);
    }

    // ------------------------------------------------------------------ trip

    @Transactional
    public Booking startTrip(Customer customer, String ref, String otp) {
        Booking b = getOwned(customer, ref);
        if (b.getStatus() != BookingStatus.CONFIRMED) {
            throw ApiException.conflict("Trip can be started only for a confirmed booking");
        }
        if (otp == null || !otp.trim().equals(b.getTripOtp())) {
            throw ApiException.badRequest("Incorrect trip OTP. Share the OTP shown in your booking with our executive.");
        }
        // collect pay-at-pickup amount
        if (b.getPaymentStatus() == PaymentStatus.PENDING) {
            Payment p = new Payment();
            p.setBooking(b);
            p.setBookingRef(b.getBookingRef());
            p.setCustomerName(customer.getFullName());
            p.setKind("CHARGE");
            p.setMethod(PaymentMethodType.PAY_AT_PICKUP);
            p.setStatus(PaymentStatus.SUCCESS);
            p.setAmount(b.getTotalAmount());
            p.setTransactionId("CNT" + System.currentTimeMillis());
            p.setMaskedDetails("Collected at pickup counter");
            p.setGatewayMessage("Amount collected at pickup");
            p.setCreatedAt(LocalDateTime.now());
            paymentRepository.save(p);
            b.setPaymentStatus(PaymentStatus.SUCCESS);
        }
        TrackingService.prepareRoute(b);
        b.setStatus(BookingStatus.ONGOING);
        b.setTripStartedAt(LocalDateTime.now());
        return bookingRepository.save(b);
    }

    @Transactional
    public Booking endTrip(Customer customer, String ref) {
        Booking b = getOwned(customer, ref);
        return endTripInternal(b);
    }

    @Transactional
    public Booking endTripInternal(Booking b) {
        if (b.getStatus() != BookingStatus.ONGOING) {
            throw ApiException.conflict("Trip is not in progress");
        }
        LocalDateTime now = LocalDateTime.now();
        double extraKm = Math.max(0, b.getDistanceKm() - b.getKmLimit());
        double extraCharge = Geo.round2(extraKm * b.getExtraKmRate());
        double lateFee = 0;
        if (now.isAfter(b.getEndTime().plusHours(1))) { // 1 hour grace period
            long lateHours = Duration.between(b.getEndTime(), now).toHours();
            lateFee = Geo.round2(lateHours * b.getVehicle().getPricePerHour() * 1.5);
        }
        b.setExtraKmCharge(extraCharge);
        b.setLateFee(lateFee);
        b.setFinalAmount(Geo.round2(b.getTotalAmount() + extraCharge + lateFee));
        b.setStatus(BookingStatus.COMPLETED);
        b.setTripEndedAt(now);
        b.setCurrentSpeed(0);

        Vehicle v = b.getVehicle();
        v.setTripsCompleted(v.getTripsCompleted() + 1);
        vehicleRepository.save(v);
        if (b.getDriver() != null) {
            Driver d = b.getDriver();
            d.setTripsCompleted(d.getTripsCompleted() + 1);
            driverRepository.save(d);
        }
        if (extraCharge + lateFee > 0) {
            Payment p = new Payment();
            p.setBooking(b);
            p.setBookingRef(b.getBookingRef());
            p.setCustomerName(b.getCustomer().getFullName());
            p.setKind("CHARGE");
            p.setMethod(b.getPaymentMethod());
            p.setStatus(PaymentStatus.SUCCESS);
            p.setAmount(extraCharge + lateFee);
            p.setTransactionId("ADJ" + System.currentTimeMillis());
            p.setMaskedDetails("Adjusted from security deposit");
            p.setGatewayMessage("Extra km / late return charges");
            p.setCreatedAt(now);
            paymentRepository.save(p);
        }
        return bookingRepository.save(b);
    }

    @Transactional
    public Booking review(Customer customer, String ref, int rating, String comment) {
        Booking b = getOwned(customer, ref);
        if (b.getStatus() != BookingStatus.COMPLETED) {
            throw ApiException.conflict("You can rate a trip after it is completed");
        }
        if (rating < 1 || rating > 5) {
            throw ApiException.badRequest("Rating must be between 1 and 5");
        }
        if (b.getRating() != null) {
            throw ApiException.conflict("You have already reviewed this trip. Thank you!");
        }
        b.setRating(rating);
        b.setReview(comment);
        Vehicle v = b.getVehicle();
        int n = Math.max(v.getTripsCompleted(), 1);
        v.setRating(Geo.round2((v.getRating() * (n - 1) + rating) / n));
        vehicleRepository.save(v);
        customer.setLoyaltyPoints(customer.getLoyaltyPoints() + 50);
        customerRepository.save(customer);
        return bookingRepository.save(b);
    }

    /** Releases vehicles held by bookings that were never paid (30 minute payment window). */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void expireUnpaidBookings() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(30);
        for (Booking b : bookingRepository.findByStatus(BookingStatus.PENDING_PAYMENT)) {
            if (b.getCreatedAt() != null && b.getCreatedAt().isBefore(cutoff)) {
                b.setStatus(BookingStatus.CANCELLED);
                b.setCancelledAt(LocalDateTime.now());
                b.setFinalAmount(0);
                b.setSpecialRequests(appendNote(b.getSpecialRequests(), "Auto-cancelled: payment not completed in 30 minutes"));
                bookingRepository.save(b);
            }
        }
    }

    private static String appendNote(String existing, String note) {
        return existing == null || existing.isBlank() ? note : existing + " | " + note;
    }

    // ------------------------------------------------------------------ queries

    public List<Booking> forCustomer(Customer customer) {
        return bookingRepository.findByCustomerIdOrderByCreatedAtDesc(customer.getId());
    }

    public Booking getOwned(Customer customer, String ref) {
        Booking b = getByRef(ref);
        if (!b.getCustomer().getId().equals(customer.getId())) {
            throw ApiException.notFound("Booking not found");
        }
        return b;
    }

    public Booking getByRef(String ref) {
        if (ref == null) {
            throw ApiException.notFound("Booking not found");
        }
        return bookingRepository.findByBookingRef(ref.trim().toUpperCase())
                .orElseThrow(() -> ApiException.notFound("Booking " + ref + " not found"));
    }

    public List<Payment> payments(Booking b) {
        return paymentRepository.findByBookingRefOrderByCreatedAtAsc(b.getBookingRef());
    }

    // ------------------------------------------------------------------ admin

    @Transactional
    public Booking adminSetStatus(String ref, String status) {
        Booking b = getByRef(ref);
        BookingStatus target;
        try {
            target = BookingStatus.valueOf(status.toUpperCase());
        } catch (Exception e) {
            throw ApiException.badRequest("Unknown status " + status);
        }
        if (target == BookingStatus.CANCELLED) {
            return cancelInternal(b, true);
        }
        if (target == BookingStatus.COMPLETED && b.getStatus() == BookingStatus.ONGOING) {
            return endTripInternal(b);
        }
        if (target == BookingStatus.ONGOING && b.getStatus() == BookingStatus.CONFIRMED) {
            TrackingService.prepareRoute(b);
            b.setTripStartedAt(LocalDateTime.now());
        }
        b.setStatus(target);
        return bookingRepository.save(b);
    }

    @Transactional
    public Booking adminAssignDriver(String ref, Long driverId) {
        Booking b = getByRef(ref);
        if (b.getVehicle().getType() != VehicleType.CAR) {
            throw ApiException.badRequest("Drivers can be assigned only to car bookings");
        }
        Driver d = driverRepository.findById(driverId).orElseThrow(() -> ApiException.notFound("Driver not found"));
        if (b.getDriver() == null || !b.getDriver().getId().equals(d.getId())) {
            if (bookingRepository.countDriverOverlaps(d.getId(), ACTIVE, b.getStartTime(), b.getEndTime()) > 0) {
                throw ApiException.conflict(d.getName() + " already has a trip in this time range");
            }
        }
        b.setDriver(d);
        if (!b.isWithDriver()) {
            b.setWithDriver(true);
        }
        return bookingRepository.save(b);
    }
}
