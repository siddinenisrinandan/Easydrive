package com.driveease.config;

import com.driveease.dto.Dtos.Quote;
import com.driveease.dto.Dtos.QuoteRequest;
import com.driveease.model.*;
import com.driveease.repository.*;
import com.driveease.service.Catalog;
import com.driveease.service.PricingService;
import com.driveease.service.TrackingService;
import com.driveease.util.PasswordHasher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Loads the starter fleet (10 cars + 10 bikes), drivers, demo customers and a few sample bookings
 * the first time the application starts.
 *
 * Demo login:  demo@driveease.in / demo123      Admin: admin / admin123
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final VehicleRepository vehicles;
    private final DriverRepository drivers;
    private final CustomerRepository customers;
    private final BookingRepository bookings;
    private final PaymentRepository payments;
    private final AlertRepository alerts;
    private final PricingService pricing;

    public DataSeeder(VehicleRepository vehicles, DriverRepository drivers, CustomerRepository customers,
                      BookingRepository bookings, PaymentRepository payments, AlertRepository alerts,
                      PricingService pricing) {
        this.vehicles = vehicles;
        this.drivers = drivers;
        this.customers = customers;
        this.bookings = bookings;
        this.payments = payments;
        this.alerts = alerts;
        this.pricing = pricing;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (vehicles.count() > 0) {
            return;
        }
        log.info("Seeding DriveEase demo data (10 cars, 10 bikes, drivers, customers, bookings)...");
        seedVehicles();
        seedDrivers();
        seedCustomersAndBookings();
        log.info("Demo data ready. Customer login: demo@driveease.in / demo123 | Admin: admin / admin123");
    }

    // ------------------------------------------------------------------ fleet

    private void seedVehicles() {
        // ---------- 10 CARS ----------
        car("Maruti Suzuki Swift", "Maruti Suzuki", "Hatchback", "Petrol", "Manual", 5, 2024, "1.2L K-Series", "22.4 km/l",
                1499, 149, 3000, 300, 9, "#dc2626", 4.6, 212, "TS09 EX 4521",
                "Bluetooth audio,Rear camera,Dual airbags,Apple CarPlay,Fuel efficient",
                "India's favourite hatchback. Zippy in city traffic, easy to park and very light on fuel - perfect for daily errands and short weekend trips.");
        car("Hyundai i20", "Hyundai", "Hatchback", "Petrol", "Automatic", 5, 2024, "1.2L Kappa IVT", "20 km/l",
                1799, 179, 3000, 300, 9, "#2563eb", 4.7, 168, "TS08 FK 7710",
                "Sunroof,Wireless charging,Bose speakers,Automatic climate,Cruise control",
                "Premium hatchback with a smooth IVT automatic, sunroof and Bose sound system. Stress-free driving in bumper-to-bumper traffic.");
        car("Honda City", "Honda", "Sedan", "Petrol", "Automatic", 5, 2024, "1.5L i-VTEC CVT", "18.4 km/l",
                2499, 249, 5000, 300, 11, "#64748b", 4.8, 190, "TS07 GH 1203",
                "Lane watch camera,Sunroof,ADAS,Leather seats,506L boot",
                "The executive sedan. Spacious rear seat, big 506 litre boot and Honda Sensing ADAS - ideal for airport runs, business travel and highway drives.");
        car("Hyundai Creta", "Hyundai", "SUV", "Diesel", "Automatic", 5, 2025, "1.5L CRDi 6AT", "19.1 km/l",
                3199, 319, 5000, 300, 12, "#f8fafc", 4.8, 245, "TS09 FB 3344",
                "Panoramic sunroof,Ventilated seats,360 camera,ADAS Level 2,Ambient lighting",
                "India's best-selling SUV. High ground clearance, panoramic sunroof and a frugal diesel engine for long family road trips.");
        car("Mahindra XUV700", "Mahindra", "SUV", "Diesel", "Automatic", 7, 2025, "2.2L mHawk AWD", "15 km/l",
                4499, 449, 8000, 300, 14, "#0f172a", 4.9, 176, "TS10 EZ 7007",
                "7 seats,ADAS,Dual 10.25in screens,Sony 3D sound,AWD",
                "Powerful 7-seater SUV with all-wheel drive and advanced driver assistance. Great for big families, hill stations and Srisailam ghat roads.");
        car("Toyota Innova Crysta", "Toyota", "MUV", "Diesel", "Manual", 7, 2024, "2.4L GD", "13 km/l",
                4299, 429, 8000, 300, 13, "#e5e7eb", 4.9, 302, "TS11 UB 2468",
                "7 captain seats,Rear AC vents,Bulletproof reliability,Large luggage space,7 airbags",
                "The legend of Indian highways. Unbeatable comfort for pilgrimages to Tirupati, family functions and group tours.");
        car("Tata Nexon EV", "Tata", "EV", "Electric", "Automatic", 5, 2025, "45 kWh / 489 km range", "489 km per charge",
                2799, 279, 5000, 250, 10, "#14b8a6", 4.7, 134, "TS09 EV 0420",
                "Zero emission,Fast charging,V2L,Connected car app,5-star safety",
                "Electric SUV with a 489 km range and 5-star safety. Silent, instant torque and zero fuel cost - free charging at all DriveEase hubs.");
        car("Mahindra Thar", "Mahindra", "Off-Road", "Diesel", "Manual", 4, 2024, "2.2L mHawk 4x4", "15.2 km/l",
                3499, 349, 8000, 250, 14, "#b45309", 4.8, 188, "TS12 TH 4040",
                "4x4,Convertible top,Off-road tyres,Adventure ready,Touchscreen",
                "The ultimate adventure machine. True 4x4 with low-range gearbox for Araku valley, Ananthagiri hills and weekend off-roading.");
        car("Toyota Fortuner", "Toyota", "SUV", "Diesel", "Automatic", 7, 2025, "2.8L 4x4 AT", "12 km/l",
                6499, 649, 15000, 300, 18, "#1e293b", 4.9, 97, "TS09 FT 9999",
                "4x4,7 seats,Leather interiors,JBL audio,Powered tailgate",
                "Full-size premium SUV with commanding road presence. Ideal for VIP travel, weddings and long-distance highway cruising.");
        car("BMW 3 Series", "BMW", "Luxury", "Petrol", "Automatic", 5, 2025, "2.0L TwinPower Turbo 258hp", "15.4 km/l",
                9999, 999, 25000, 250, 25, "#1d4ed8", 5.0, 54, "TS09 BM 0330",
                "Luxury interiors,Harman Kardon,Ambient lights,Sport mode,Gesture control",
                "The ultimate driving machine. 258 hp of pure luxury for weddings, special occasions and corporate guests.");

        // ---------- 10 BIKES & SCOOTERS ----------
        bike("Honda Activa 6G", "Honda", "Scooter", "Petrol", "Automatic", 2024, "110cc", "50 km/l",
                399, 49, 1000, 150, 3, "#9ca3af", 4.6, 410, "TS09 ES 1111",
                "Gearless,Under-seat storage,Silent start,Easy to ride",
                "India's most trusted scooter. Gearless, comfortable and super easy for daily commutes and city errands.");
        bike("TVS Jupiter", "TVS", "Scooter", "Petrol", "Automatic", 2024, "113cc", "48 km/l",
                379, 45, 1000, 150, 3, "#7c3aed", 4.5, 286, "TS08 EJ 2222",
                "USB charging,Big storage,Eco mode,Comfortable seat",
                "Spacious and comfortable family scooter with USB charging and a large boot for your bags.");
        bike("Ather 450X", "Ather", "Scooter", "Electric", "Automatic", 2025, "6.4 kW / 150 km range", "150 km per charge",
                599, 69, 1500, 120, 2, "#111827", 4.8, 198, "TS09 EV 4500",
                "Google Maps navigation,Touchscreen,Fast charging,Warp mode,Zero emission",
                "Smart electric scooter with a touchscreen dashboard, navigation and 0-40 in 3.3 seconds. No fuel costs at all.");
        bike("Ola S1 Pro", "Ola", "Scooter", "Electric", "Automatic", 2025, "11 kW / 195 km range", "195 km per charge",
                549, 65, 1500, 120, 2, "#f43f5e", 4.4, 156, "TS10 EV 1010",
                "Cruise control,Music,Hill hold,Reverse mode,Zero emission",
                "Feature-loaded electric scooter with 195 km range, cruise control and a huge 34 litre boot.");
        bike("Royal Enfield Classic 350", "Royal Enfield", "Cruiser", "Petrol", "Manual", 2024, "349cc J-Series", "35 km/l",
                1199, 129, 3000, 200, 5, "#7f1d1d", 4.8, 334, "TS09 RE 3500",
                "Iconic thump,Dual channel ABS,Tripper navigation,Comfortable cruiser",
                "The iconic Bullet. Relaxed riding position and that famous thump - made for long highway rides and weekend getaways.");
        bike("Royal Enfield Himalayan 450", "Royal Enfield", "Adventure", "Petrol", "Manual", 2025, "452cc Sherpa", "30 km/l",
                1799, 189, 5000, 200, 6, "#365314", 4.9, 121, "TS09 HM 4500",
                "Long travel suspension,Ride-by-wire,TFT navigation,Luggage mounts,Switchable ABS",
                "Purpose-built adventure tourer. Long-travel suspension and 21-inch front wheel for Araku, Lambasingi and beyond.");
        bike("Bajaj Pulsar NS200", "Bajaj", "Sports", "Petrol", "Manual", 2024, "199.5cc", "36 km/l",
                899, 99, 2000, 150, 4, "#0369a1", 4.6, 219, "TS07 PL 2000",
                "Perimeter frame,ABS,Digital console,Sporty handling",
                "Street-fighter with sharp handling and punchy performance. Fun on city roads and ORR alike.");
        bike("KTM Duke 390", "KTM", "Sports", "Petrol", "Manual", 2025, "399cc LC4c", "28 km/l",
                1999, 219, 5000, 150, 6, "#ea580c", 4.8, 102, "TS09 KT 3900",
                "Quickshifter,Cornering ABS,TFT display,Launch control,WP suspension",
                "The ready-to-race naked bike. 45 hp, quickshifter and cornering ABS for serious thrill seekers.");
        bike("Yamaha R15 V4", "Yamaha", "Sports", "Petrol", "Manual", 2024, "155cc VVA", "45 km/l",
                1099, 119, 2500, 150, 4, "#1e40af", 4.7, 176, "TS08 YR 1515",
                "Racing DNA,Traction control,Quickshifter,LED lights,Dual channel ABS",
                "Track-inspired supersport with racing ergonomics and traction control. Head turner everywhere.");
        bike("Hero Splendor Plus", "Hero", "Commuter", "Petrol", "Manual", 2024, "97.2cc", "70 km/l",
                349, 39, 800, 150, 3, "#000000", 4.5, 389, "TS09 HS 7070",
                "Best mileage,Low maintenance,Light weight,i3S start-stop",
                "India's most fuel-efficient commuter bike at 70 km/l. The cheapest way to get around the city.");
    }

    private void car(String name, String brand, String category, String fuel, String transmission, int seats, int year,
                     String engine, String mileage, double day, double hour, double deposit, int kmPerDay, double extraKm,
                     String color, double rating, int trips, String reg, String features, String description) {
        Vehicle v = base(name, brand, VehicleType.CAR, category, fuel, transmission, seats, year, engine, mileage,
                day, hour, deposit, kmPerDay, extraKm, color, rating, trips, reg, features, description);
        vehicles.save(v);
    }

    private void bike(String name, String brand, String category, String fuel, String transmission, int year,
                      String engine, String mileage, double day, double hour, double deposit, int kmPerDay, double extraKm,
                      String color, double rating, int trips, String reg, String features, String description) {
        Vehicle v = base(name, brand, VehicleType.BIKE, category, fuel, transmission, 2, year, engine, mileage,
                day, hour, deposit, kmPerDay, extraKm, color, rating, trips, reg, features, description);
        vehicles.save(v);
    }

    private Vehicle base(String name, String brand, VehicleType type, String category, String fuel, String transmission,
                         int seats, int year, String engine, String mileage, double day, double hour, double deposit,
                         int kmPerDay, double extraKm, String color, double rating, int trips, String reg,
                         String features, String description) {
        Vehicle v = new Vehicle();
        v.setName(name);
        v.setBrand(brand);
        v.setType(type);
        v.setCategory(category);
        v.setFuelType(fuel);
        v.setTransmission(transmission);
        v.setSeats(seats);
        v.setModelYear(year);
        v.setEngine(engine);
        v.setMileage(mileage);
        v.setPricePerDay(day);
        v.setPricePerHour(hour);
        v.setDeposit(deposit);
        v.setKmLimitPerDay(kmPerDay);
        v.setExtraKmCharge(extraKm);
        v.setColor(color);
        v.setRating(rating);
        v.setTripsCompleted(trips);
        v.setRegistrationNumber(reg);
        v.setFeatures(features);
        v.setDescription(description);
        v.setCity("Hyderabad");
        v.setAvailable(true);
        return v;
    }

    // ------------------------------------------------------------------ drivers

    private void seedDrivers() {
        driver("Ramesh Kumar", "+91 98480 11223", "TS0920090012345", 14, 4.9, "Telugu, Hindi, English");
        driver("Suresh Reddy", "+91 98665 22334", "TS0720110023456", 11, 4.8, "Telugu, English");
        driver("Venkatesh Rao", "+91 99490 33445", "AP0920120034567", 10, 4.8, "Telugu, Hindi, Tamil");
        driver("Mohammed Irfan", "+91 90000 44556", "TS0820130045678", 9, 4.7, "Urdu, Hindi, Telugu, English");
        driver("Srinivas Goud", "+91 91234 55667", "TS1020140056789", 8, 4.7, "Telugu, Hindi");
        driver("Anil Varma", "+91 97010 66778", "AP3120150067890", 7, 4.6, "Telugu, English, Kannada");
        driver("Lakshmi Prasad", "+91 96660 77889", "TS0920160078901", 6, 4.8, "Telugu, Hindi, English");
        driver("Kiran Naidu", "+91 95500 88990", "AP3920170089012", 5, 4.6, "Telugu, Hindi");
    }

    private void driver(String name, String phone, String licence, int exp, double rating, String languages) {
        Driver d = new Driver();
        d.setName(name);
        d.setPhone(phone);
        d.setEmail(name.toLowerCase().replace(" ", ".") + "@drivers.driveease.in");
        d.setLicenseNumber(licence);
        d.setExperienceYears(exp);
        d.setRating(rating);
        d.setLanguages(languages);
        d.setCity("Hyderabad");
        d.setDailyCharge(Catalog.DRIVER_PER_DAY);
        d.setTripsCompleted(exp * 45);
        d.setAvailable(true);
        drivers.save(d);
    }

    // ------------------------------------------------------------------ customers & bookings

    private void seedCustomersAndBookings() {
        Customer demo = customer("Demo Customer", "demo@driveease.in", "+91 98765 43210", "demo123",
                "TS0920210012345", "Plot 42, Madhapur, Hyderabad", "Ravi (Brother)", "+91 91234 56789");
        Customer priya = customer("Priya Sharma", "priya@example.com", "+91 99887 76655", "priya123",
                "TS0720190034567", "Banjara Hills, Hyderabad", "Anita Sharma", "+91 98490 12345");
        Customer arjun = customer("Arjun Rao", "arjun@example.com", "+91 90100 20030", "arjun123",
                "AP0920180045678", "Benz Circle, Vijayawada", "Lakshmi Rao", "+91 94400 55667");

        List<Vehicle> fleet = vehicles.findAll();
        List<Driver> team = drivers.findAll();
        Vehicle swift = byName(fleet, "Maruti Suzuki Swift");
        Vehicle creta = byName(fleet, "Hyundai Creta");
        Vehicle innova = byName(fleet, "Toyota Innova Crysta");
        Vehicle classic = byName(fleet, "Royal Enfield Classic 350");
        Vehicle ather = byName(fleet, "Ather 450X");
        Vehicle thar = byName(fleet, "Mahindra Thar");
        Vehicle city = byName(fleet, "Honda City");
        Vehicle himalayan = byName(fleet, "Royal Enfield Himalayan 450");

        LocalDate today = LocalDate.now();

        // completed trips over the last week (gives the admin dashboard real numbers)
        completed(priya, city, null, "Hitech City (Cyber Towers)", "RGIA Airport, Shamshabad",
                today.minusDays(6).atTime(8, 0), 1, "STANDARD", PaymentMethodType.UPI, "pr***@okhdfc", 5, "Spotless car, very smooth pickup!");
        completed(arjun, innova, team.get(1), "Vijayawada (Benz Circle)", "Tirupati (Railway Station)",
                today.minusDays(5).atTime(6, 0), 2, "PREMIUM", PaymentMethodType.CARD, "VISA **** 4242", 5, "Suresh garu drove very safely. Family loved it.");
        completed(demo, classic, null, "Kukatpally (KPHB)", "Kukatpally (KPHB)",
                today.minusDays(4).atTime(7, 0), 1, "STANDARD", PaymentMethodType.UPI, "de***@okicici", 4, "Great bike for a Sunday ride.");
        completed(priya, ather, null, "Gachibowli (DLF Cyber City)", "Gachibowli (DLF Cyber City)",
                today.minusDays(3).atTime(9, 0), 1, "BASIC", PaymentMethodType.WALLET, "PhonePe (******6655)", 5, "Loved the EV, zero fuel cost.");
        completed(arjun, thar, null, "Visakhapatnam (RK Beach)", "Visakhapatnam (RK Beach)",
                today.minusDays(2).atTime(6, 30), 2, "PREMIUM", PaymentMethodType.NET_BANKING, "HDFC Bank", 5, "Araku trip in a Thar - unforgettable!");
        completed(priya, himalayan, null, "Banjara Hills Road No. 12", "Banjara Hills Road No. 12",
                today.minusDays(1).atTime(10, 0), 1, "STANDARD", PaymentMethodType.CARD, "RUPAY **** 1234", null, null);

        // a live trip so the tracking map shows movement right away
        Booking live = booking(demo, creta, null, "Hitech City (Cyber Towers)", "RGIA Airport, Shamshabad",
                LocalDateTime.now().minusMinutes(20), LocalDateTime.now().plusDays(1), "PREMIUM");
        live.setStatus(BookingStatus.ONGOING);
        live.setPaymentStatus(PaymentStatus.SUCCESS);
        live.setPaymentMethod(PaymentMethodType.UPI);
        live.setTripStartedAt(LocalDateTime.now().minusMinutes(5));
        TrackingService.prepareRoute(live);
        bookings.save(live);
        payment(live, PaymentMethodType.UPI, "de***@okicici", live.getTotalAmount(), "CHARGE", LocalDateTime.now().minusMinutes(25));

        // an upcoming confirmed trip with a driver
        Booking upcoming = booking(demo, innova, team.get(0), "Secunderabad Railway Station", "Vijayawada (Benz Circle)",
                today.plusDays(3).atTime(LocalTime.of(6, 0)), today.plusDays(5).atTime(LocalTime.of(20, 0)), "STANDARD");
        upcoming.setStatus(BookingStatus.CONFIRMED);
        upcoming.setPaymentStatus(PaymentStatus.SUCCESS);
        upcoming.setPaymentMethod(PaymentMethodType.CARD);
        bookings.save(upcoming);
        payment(upcoming, PaymentMethodType.CARD, "VISA **** 1111", upcoming.getTotalAmount(), "CHARGE", LocalDateTime.now().minusHours(3));

        // an upcoming pay-at-pickup booking
        Booking cash = booking(arjun, swift, null, "Charminar", "Charminar",
                today.plusDays(1).atTime(9, 0), today.plusDays(1).atTime(21, 0), "BASIC");
        cash.setStatus(BookingStatus.CONFIRMED);
        cash.setPaymentStatus(PaymentStatus.PENDING);
        cash.setPaymentMethod(PaymentMethodType.PAY_AT_PICKUP);
        bookings.save(cash);

        // one open support alert for the admin console
        Alert a = new Alert();
        a.setBookingRef(live.getBookingRef());
        a.setType(AlertType.SUPPORT);
        a.setStatus(AlertStatus.OPEN);
        a.setRaisedBy("CUSTOMER");
        a.setMessage("AC is not cooling enough, please advise.");
        a.setCustomerName(demo.getFullName());
        a.setCustomerPhone(demo.getPhone());
        a.setVehicleName(creta.getName() + " (" + creta.getRegistrationNumber() + ")");
        a.setLat(live.getPickupLat());
        a.setLng(live.getPickupLng());
        a.setCreatedAt(LocalDateTime.now().minusMinutes(2));
        alerts.save(a);
    }

    private Customer customer(String name, String email, String phone, String password, String licence,
                              String address, String emergencyName, String emergencyPhone) {
        Customer c = new Customer();
        c.setFullName(name);
        c.setEmail(email);
        c.setPhone(phone);
        c.setPasswordHash(PasswordHasher.hash(password));
        c.setLicenseNumber(licence);
        c.setLicenseExpiry("2041-06-30");
        c.setDateOfBirth("1996-04-15");
        c.setAddress(address);
        c.setCity(address.substring(address.lastIndexOf(',') + 1).trim());
        c.setEmergencyContactName(emergencyName);
        c.setEmergencyContactPhone(emergencyPhone);
        c.setLoyaltyPoints(250);
        c.setCreatedAt(LocalDateTime.now().minusDays(30));
        return customers.save(c);
    }

    private void completed(Customer c, Vehicle v, Driver d, String from, String to, LocalDateTime start, int days,
                           String plan, PaymentMethodType method, String masked, Integer rating, String review) {
        Booking b = booking(c, v, d, from, to, start, start.plusDays(days), plan);
        b.setStatus(BookingStatus.COMPLETED);
        b.setPaymentStatus(PaymentStatus.SUCCESS);
        b.setPaymentMethod(method);
        b.setTripStartedAt(start);
        b.setTripEndedAt(start.plusDays(days).minusHours(1));
        TrackingService.prepareRoute(b);
        b.setTripProgress(1.0);
        b.setCurrentLat(b.getDropLat());
        b.setCurrentLng(b.getDropLng());
        b.setDistanceKm(Math.round(b.getRouteLengthKm() * 1.1));
        b.setRating(rating);
        b.setReview(review);
        b.setCreatedAt(start.minusDays(2));
        bookings.save(b);
        payment(b, method, masked, b.getTotalAmount(), "CHARGE", start.minusDays(2).plusDays(2).minusHours(2));
    }

    private Booking booking(Customer c, Vehicle v, Driver d, String from, String to, LocalDateTime start,
                            LocalDateTime end, String plan) {
        Catalog.Location pickup = Catalog.location(from).orElseThrow();
        Catalog.Location drop = Catalog.location(to).orElseThrow();
        Quote q = pricing.quote(v, new QuoteRequest(v.getId(), start, end, plan, d != null, List.of(), null,
                pickup.name(), drop.name()));
        Booking b = new Booking();
        b.setBookingRef("DE" + PasswordHasher.randomToken(6).toUpperCase().replaceAll("[^A-Z0-9]", "X").substring(0, 7));
        b.setCustomer(c);
        b.setVehicle(v);
        b.setDriver(d);
        b.setWithDriver(d != null);
        b.setPickupLocation(pickup.name());
        b.setPickupLat(pickup.lat());
        b.setPickupLng(pickup.lng());
        b.setDropLocation(drop.name());
        b.setDropLat(drop.lat());
        b.setDropLng(drop.lng());
        b.setStartTime(start);
        b.setEndTime(end);
        b.setInsurancePlan(InsurancePlan.valueOf(plan));
        b.setAddons("");
        b.setTotalHours(q.totalHours());
        b.setBillableDays(q.billableDays());
        b.setBaseAmount(q.baseAmount());
        b.setWeekendSurcharge(q.weekendSurcharge());
        b.setLongRentalDiscount(q.longRentalDiscount());
        b.setInsuranceAmount(q.insuranceAmount());
        b.setDriverAmount(q.driverAmount());
        b.setAddonsAmount(q.addonsAmount());
        b.setOneWayFee(q.oneWayFee());
        b.setCouponDiscount(0);
        b.setTaxAmount(q.taxAmount());
        b.setTotalAmount(q.totalAmount());
        b.setFinalAmount(q.totalAmount());
        b.setDepositAmount(q.depositAmount());
        b.setKmLimit(q.kmLimit());
        b.setExtraKmRate(q.extraKmRate());
        b.setTripOtp(PasswordHasher.randomDigits(4));
        b.setShareCode(PasswordHasher.randomToken(9));
        b.setCreatedAt(LocalDateTime.now().minusHours(4));
        return b;
    }

    private void payment(Booking b, PaymentMethodType method, String masked, double amount, String kind, LocalDateTime at) {
        Payment p = new Payment();
        p.setBooking(b);
        p.setBookingRef(b.getBookingRef());
        p.setCustomerName(b.getCustomer().getFullName());
        p.setKind(kind);
        p.setMethod(method);
        p.setStatus(PaymentStatus.SUCCESS);
        p.setAmount(amount);
        p.setTransactionId("TXN" + Math.abs(b.getBookingRef().hashCode()) + (int) (Math.random() * 1000));
        p.setMaskedDetails(masked);
        p.setGatewayMessage("Payment successful");
        p.setCreatedAt(at);
        payments.save(p);
    }

    private static Vehicle byName(List<Vehicle> fleet, String name) {
        return fleet.stream().filter(v -> v.getName().equals(name)).findFirst().orElseThrow();
    }
}
