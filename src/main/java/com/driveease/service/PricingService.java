package com.driveease.service;

import com.driveease.dto.Dtos.Quote;
import com.driveease.dto.Dtos.QuoteLine;
import com.driveease.dto.Dtos.QuoteRequest;
import com.driveease.model.InsurancePlan;
import com.driveease.model.Vehicle;
import com.driveease.model.VehicleType;
import com.driveease.repository.VehicleRepository;
import com.driveease.web.ApiException;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.driveease.util.Geo.round2;

/**
 * Transparent pricing engine.
 *
 * <pre>
 * rental    = full days x day rate + remaining hours x hour rate (capped at one day rate)
 * + weekend surcharge (10% of day rate for every Saturday / Sunday)
 * - long rental discount (3+ days 5%, 7+ days 10%, 30+ days 20%)
 * + insurance plan x days + driver x days + add-ons + one-way fee
 * - coupon
 * + GST 18%
 * security deposit is refundable and collected separately (halved with Premium insurance)
 * </pre>
 */
@Service
public class PricingService {

    private final VehicleRepository vehicleRepository;

    public PricingService(VehicleRepository vehicleRepository) {
        this.vehicleRepository = vehicleRepository;
    }

    public Quote quote(QuoteRequest req) {
        if (req.vehicleId() == null) {
            throw ApiException.badRequest("Please choose a vehicle");
        }
        Vehicle v = vehicleRepository.findById(req.vehicleId())
                .orElseThrow(() -> ApiException.notFound("Vehicle not found"));
        return quote(v, req);
    }

    public Quote quote(Vehicle v, QuoteRequest req) {
        LocalDateTime start = req.startTime();
        LocalDateTime end = req.endTime();
        if (start == null || end == null) {
            throw ApiException.badRequest("Please select pickup and return date/time");
        }
        if (!end.isAfter(start)) {
            throw ApiException.badRequest("Return time must be after pickup time");
        }
        long minutes = Duration.between(start, end).toMinutes();
        long hours = (minutes + 59) / 60;
        if (hours < Catalog.MIN_RENTAL_HOURS) {
            throw ApiException.badRequest("Minimum rental duration is " + Catalog.MIN_RENTAL_HOURS + " hours");
        }
        if (hours > Catalog.MAX_RENTAL_DAYS * 24L) {
            throw ApiException.badRequest("Maximum rental duration is " + Catalog.MAX_RENTAL_DAYS + " days");
        }

        InsurancePlan plan = parsePlan(req.insurancePlan());
        VehicleType type = v.getType();
        int billableDays = (int) Math.ceil(hours / 24.0);
        List<QuoteLine> lines = new ArrayList<>();

        // 1. rental charge
        long fullDays = hours / 24;
        long remHours = hours % 24;
        double base = fullDays * v.getPricePerDay() + Math.min(remHours * v.getPricePerHour(), v.getPricePerDay());
        base = round2(base);
        String baseNote = fullDays > 0
                ? fullDays + " day(s) x Rs." + fmt(v.getPricePerDay()) + (remHours > 0 ? " + " + remHours + " hr(s)" : "")
                : hours + " hr(s) x Rs." + fmt(v.getPricePerHour()) + " (capped at day rate)";
        lines.add(new QuoteLine("Rental charge", base, baseNote));

        // 2. weekend surcharge
        int weekendDays = 0;
        LocalDate d = start.toLocalDate();
        for (int i = 0; i < billableDays; i++) {
            DayOfWeek dow = d.plusDays(i).getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                weekendDays++;
            }
        }
        double weekend = round2(weekendDays * v.getPricePerDay() * 0.10);
        if (weekend > 0) {
            lines.add(new QuoteLine("Weekend demand surcharge", weekend, weekendDays + " weekend day(s) at 10%"));
        }

        // 3. long rental discount
        double pct = billableDays >= 30 ? 0.20 : billableDays >= 7 ? 0.10 : billableDays >= 3 ? 0.05 : 0;
        double longDisc = round2(base * pct);
        if (longDisc > 0) {
            lines.add(new QuoteLine("Long rental discount", -longDisc, (int) (pct * 100) + "% off for " + billableDays + " days"));
        }

        // 4. insurance
        double insurance = round2(plan.perDayFor(type) * billableDays);
        lines.add(new QuoteLine("Insurance - " + plan.getLabel(), insurance,
                insurance == 0 ? "Included" : "Rs." + fmt(plan.perDayFor(type)) + " x " + billableDays + " day(s)"));

        // 5. driver (cars only)
        boolean withDriver = req.withDriver() && type == VehicleType.CAR;
        double driver = withDriver ? round2(Catalog.DRIVER_PER_DAY * billableDays) : 0;
        if (withDriver) {
            lines.add(new QuoteLine("Professional driver", driver, "Rs." + fmt(Catalog.DRIVER_PER_DAY) + " x " + billableDays + " day(s)"));
        }

        // 6. add-ons
        List<String> addonCodes = new ArrayList<>();
        double addonsTotal = 0;
        if (req.addons() != null) {
            for (String code : req.addons()) {
                Catalog.Addon a = Catalog.addon(code).orElse(null);
                if (a == null || !a.appliesTo(type) || addonCodes.contains(a.code())) {
                    continue;
                }
                double cost = a.perDay() ? a.price() * billableDays : a.price();
                addonsTotal += cost;
                addonCodes.add(a.code());
                lines.add(new QuoteLine(a.name(), round2(cost), a.perDay() ? "Rs." + fmt(a.price()) + " x " + billableDays + " day(s)" : "One-time"));
            }
        }
        addonsTotal = round2(addonsTotal);

        // 7. one-way fee
        double oneWay = 0;
        if (req.pickupLocation() != null && req.dropLocation() != null
                && !req.pickupLocation().isBlank() && !req.dropLocation().isBlank()
                && !req.pickupLocation().equalsIgnoreCase(req.dropLocation())) {
            oneWay = type == VehicleType.CAR ? Catalog.ONE_WAY_FEE_CAR : Catalog.ONE_WAY_FEE_BIKE;
            lines.add(new QuoteLine("One-way drop fee", oneWay, "Different return location"));
        }

        // 8. coupon
        String couponMessage = null;
        boolean couponApplied = false;
        double coupon = 0;
        String code = req.couponCode() == null ? "" : req.couponCode().trim().toUpperCase(Locale.ROOT);
        if (!code.isEmpty()) {
            switch (code) {
                case "FIRST20" -> coupon = Math.min(base * 0.20, 1000);
                case "WEEKEND10" -> {
                    if (weekendDays > 0) {
                        coupon = base * 0.10;
                    } else {
                        couponMessage = "WEEKEND10 needs a trip that includes Saturday or Sunday";
                    }
                }
                case "BIKE15" -> {
                    if (type == VehicleType.BIKE) {
                        coupon = Math.min(base * 0.15, 500);
                    } else {
                        couponMessage = "BIKE15 is valid only on bikes and scooters";
                    }
                }
                case "EV25" -> {
                    if ("Electric".equalsIgnoreCase(v.getFuelType())) {
                        coupon = Math.min(base * 0.25, 1500);
                    } else {
                        couponMessage = "EV25 is valid only on electric vehicles";
                    }
                }
                case "LONGDRIVE" -> {
                    if (billableDays >= 5) {
                        coupon = 750;
                    } else {
                        couponMessage = "LONGDRIVE needs a trip of 5 days or more";
                    }
                }
                default -> couponMessage = "Invalid coupon code";
            }
            coupon = round2(coupon);
            if (coupon > 0) {
                couponApplied = true;
                couponMessage = "Coupon " + code + " applied! You saved Rs." + fmt(coupon);
                lines.add(new QuoteLine("Coupon " + code, -coupon, "Discount"));
            }
        }

        double subtotal = round2(base + weekend - longDisc + insurance + driver + addonsTotal + oneWay - coupon);
        if (subtotal < 0) {
            subtotal = 0;
        }
        double tax = round2(subtotal * Catalog.GST_RATE);
        lines.add(new QuoteLine("GST (18%)", tax, "Government tax"));
        double total = Math.round(subtotal + tax);

        double deposit = plan == InsurancePlan.PREMIUM ? v.getDeposit() / 2 : v.getDeposit();
        int kmLimit = v.getKmLimitPerDay() * billableDays;

        return new Quote(v.getId(), v.getName(), hours, billableDays, lines,
                base, weekend, longDisc, insurance, driver, addonsTotal, oneWay,
                coupon, couponApplied, couponMessage,
                subtotal, tax, total, round2(deposit),
                kmLimit, v.getExtraKmCharge(), plan.deductibleFor(type),
                plan.name(), withDriver, addonCodes);
    }

    public static InsurancePlan parsePlan(String plan) {
        if (plan == null || plan.isBlank()) {
            return InsurancePlan.STANDARD;
        }
        try {
            return InsurancePlan.valueOf(plan.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown insurance plan: " + plan);
        }
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(round2(v));
    }
}
