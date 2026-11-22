package com.driveease.web;

import com.driveease.dto.Dtos.*;
import com.driveease.model.*;
import com.driveease.service.AlertService;
import com.driveease.service.AuthService;
import com.driveease.service.BookingService;
import com.driveease.service.TrackingService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.driveease.web.AdminController.ADMIN_HEADER;
import static com.driveease.web.AuthController.TOKEN_HEADER;

/** Customer bookings, payments, trip start / end, reviews, SOS alerts and live tracking. */
@RestController
@RequestMapping("/api")
public class BookingController {

    private final AuthService authService;
    private final BookingService bookingService;
    private final TrackingService trackingService;
    private final AlertService alertService;

    public BookingController(AuthService authService, BookingService bookingService,
                             TrackingService trackingService, AlertService alertService) {
        this.authService = authService;
        this.bookingService = bookingService;
        this.trackingService = trackingService;
        this.alertService = alertService;
    }

    @PostMapping("/bookings")
    public Booking create(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                          @RequestBody CreateBookingRequest req) {
        return bookingService.create(authService.requireCustomer(token), req);
    }

    @GetMapping("/bookings")
    public List<Booking> mine(@RequestHeader(value = TOKEN_HEADER, required = false) String token) {
        return bookingService.forCustomer(authService.requireCustomer(token));
    }

    /** Booking + its payments: everything the invoice page needs. */
    @GetMapping("/bookings/{ref}")
    public Map<String, Object> get(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                   @PathVariable String ref) {
        Booking b = bookingService.getOwned(authService.requireCustomer(token), ref);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("booking", b);
        m.put("payments", bookingService.payments(b));
        return m;
    }

    @PostMapping("/bookings/{ref}/pay")
    public Booking pay(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                       @PathVariable String ref, @RequestBody PaymentRequest req) {
        return bookingService.pay(authService.requireCustomer(token), ref, req);
    }

    @PostMapping("/bookings/{ref}/cancel")
    public Booking cancel(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                          @PathVariable String ref) {
        return bookingService.cancel(authService.requireCustomer(token), ref);
    }

    @PostMapping("/bookings/{ref}/start")
    public Booking start(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                         @PathVariable String ref, @RequestBody StartTripRequest req) {
        return bookingService.startTrip(authService.requireCustomer(token), ref, req.otp());
    }

    @PostMapping("/bookings/{ref}/end")
    public Booking end(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                       @PathVariable String ref) {
        return bookingService.endTrip(authService.requireCustomer(token), ref);
    }

    @PostMapping("/bookings/{ref}/review")
    public Booking review(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                          @PathVariable String ref, @RequestBody ReviewRequest req) {
        return bookingService.review(authService.requireCustomer(token), ref, req.rating(), req.comment());
    }

    // ------------------------------------------------------------------ SOS / support

    @PostMapping("/bookings/{ref}/alerts")
    public Map<String, Object> raiseAlert(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                          @PathVariable String ref, @RequestBody AlertRequest req) {
        Booking b = bookingService.getOwned(authService.requireCustomer(token), ref);
        Alert a = alertService.raise(b, req);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("alert", a);
        m.put("response", AlertService.responseText(a.getType()));
        m.put("emergencyNumbers", AlertService.EMERGENCY_NUMBERS);
        return m;
    }

    @GetMapping("/bookings/{ref}/alerts")
    public List<Alert> alerts(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                              @PathVariable String ref) {
        Booking b = bookingService.getOwned(authService.requireCustomer(token), ref);
        return alertService.forBooking(b);
    }

    // ------------------------------------------------------------------ live tracking

    /**
     * Allowed for: the customer who booked, an admin, or anyone holding the share code (family link, limited data).
     */
    @GetMapping("/tracking/{ref}")
    public TrackingView tracking(@RequestHeader(value = TOKEN_HEADER, required = false) String token,
                                 @RequestHeader(value = ADMIN_HEADER, required = false) String adminToken,
                                 @PathVariable String ref,
                                 @RequestParam(required = false) String share) {
        Booking b = bookingService.getByRef(ref);
        if (authService.isAdmin(adminToken)) {
            return trackingService.view(b, false);
        }
        Customer c = authService.optionalCustomer(token);
        if (c != null && c.getId().equals(b.getCustomer().getId())) {
            return trackingService.view(b, false);
        }
        if (share != null && share.equals(b.getShareCode())) {
            return trackingService.view(b, true);
        }
        throw ApiException.unauthorized("Please login to track this trip");
    }
}
