package com.driveease.service;

import com.driveease.dto.Dtos.Quote;
import com.driveease.dto.Dtos.QuoteRequest;
import com.driveease.model.Vehicle;
import com.driveease.model.VehicleType;
import com.driveease.web.ApiException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PricingServiceTest {

    private final PricingService pricing = new PricingService(null);

    private static Vehicle car() {
        Vehicle v = new Vehicle();
        v.setId(1L);
        v.setName("Test Car");
        v.setType(VehicleType.CAR);
        v.setFuelType("Petrol");
        v.setPricePerDay(2000);
        v.setPricePerHour(200);
        v.setDeposit(5000);
        v.setKmLimitPerDay(300);
        v.setExtraKmCharge(10);
        return v;
    }

    /** A Tuesday, so no weekend surcharge. */
    private static final LocalDateTime TUE = LocalDateTime.of(2026, 12, 1, 9, 0);

    @Test
    void oneDayWithStandardInsurance() {
        Quote q = pricing.quote(car(), new QuoteRequest(1L, TUE, TUE.plusDays(1), "STANDARD", false,
                List.of(), null, null, null));
        assertEquals(1, q.billableDays());
        assertEquals(2000, q.baseAmount());
        assertEquals(199, q.insuranceAmount());
        // (2000 + 199) * 1.18 = 2594.82 -> 2595
        assertEquals(2595, q.totalAmount());
        assertEquals(300, q.kmLimit());
    }

    @Test
    void hourlyRateIsCappedAtDayRate() {
        Quote q = pricing.quote(car(), new QuoteRequest(1L, TUE, TUE.plusHours(14), "BASIC", false,
                List.of(), null, null, null));
        assertEquals(2000, q.baseAmount()); // 14 x 200 = 2800, capped at 2000
    }

    @Test
    void weeklyRentalGetsDiscountAndDriver() {
        Quote q = pricing.quote(car(), new QuoteRequest(1L, TUE, TUE.plusDays(7), "BASIC", true,
                List.of("GPS"), null, null, null));
        assertEquals(7, q.billableDays());
        assertTrue(q.longRentalDiscount() > 0);
        assertEquals(7 * Catalog.DRIVER_PER_DAY, q.driverAmount());
        assertEquals(7 * 99, q.addonsAmount());
        assertTrue(q.weekendSurcharge() > 0); // includes Saturday and Sunday
    }

    @Test
    void invalidCouponDoesNotFailTheQuote() {
        Quote q = pricing.quote(car(), new QuoteRequest(1L, TUE, TUE.plusDays(1), "BASIC", false,
                List.of(), "NOPE", null, null));
        assertFalse(q.couponApplied());
        assertEquals("Invalid coupon code", q.couponMessage());
    }

    @Test
    void premiumHalvesDeposit() {
        Quote q = pricing.quote(car(), new QuoteRequest(1L, TUE, TUE.plusDays(1), "PREMIUM", false,
                List.of(), null, null, null));
        assertEquals(2500, q.depositAmount());
        assertEquals(0, q.deductible());
    }

    @Test
    void rejectsTooShortRental() {
        assertThrows(ApiException.class, () -> pricing.quote(car(), new QuoteRequest(1L, TUE, TUE.plusHours(2),
                "BASIC", false, List.of(), null, null, null)));
    }
}
