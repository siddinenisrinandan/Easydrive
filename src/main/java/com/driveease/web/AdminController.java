package com.driveease.web;

import com.driveease.dto.Dtos.*;
import com.driveease.model.*;
import com.driveease.repository.*;
import com.driveease.service.AlertService;
import com.driveease.service.AuthService;
import com.driveease.service.BookingService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/** Operations dashboard for the rental company. All endpoints need the X-Admin-Token header. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    public static final String ADMIN_HEADER = "X-Admin-Token";

    private final AuthService authService;
    private final BookingService bookingService;
    private final AlertService alertService;
    private final BookingRepository bookingRepository;
    private final VehicleRepository vehicleRepository;
    private final DriverRepository driverRepository;
    private final CustomerRepository customerRepository;
    private final PaymentRepository paymentRepository;
    private final AlertRepository alertRepository;

    public AdminController(AuthService authService, BookingService bookingService, AlertService alertService,
                           BookingRepository bookingRepository, VehicleRepository vehicleRepository,
                           DriverRepository driverRepository, CustomerRepository customerRepository,
                           PaymentRepository paymentRepository, AlertRepository alertRepository) {
        this.authService = authService;
        this.bookingService = bookingService;
        this.alertService = alertService;
        this.bookingRepository = bookingRepository;
        this.vehicleRepository = vehicleRepository;
        this.driverRepository = driverRepository;
        this.customerRepository = customerRepository;
        this.paymentRepository = paymentRepository;
        this.alertRepository = alertRepository;
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody AdminLoginRequest req) {
        return Map.of("token", authService.adminLogin(req.username(), req.password()));
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestHeader(value = ADMIN_HEADER, required = false) String token) {
        authService.requireAdmin(token);
        List<Booking> bookings = bookingRepository.findAll();
        List<Payment> payments = paymentRepository.findAll();
        List<Vehicle> fleet = vehicleRepository.findAll();

        double charges = payments.stream().filter(p -> "CHARGE".equals(p.getKind()) && p.getStatus() == PaymentStatus.SUCCESS)
                .mapToDouble(Payment::getAmount).sum();
        double refunds = payments.stream().filter(p -> "REFUND".equals(p.getKind()))
                .mapToDouble(Payment::getAmount).sum();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (BookingStatus s : BookingStatus.values()) {
            byStatus.put(s.name(), bookings.stream().filter(b -> b.getStatus() == s).count());
        }
        long ongoing = byStatus.get(BookingStatus.ONGOING.name());

        // revenue for the last 7 days
        List<Map<String, Object>> daily = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 6; i >= 0; i--) {
            LocalDate d = today.minusDays(i);
            double amount = payments.stream()
                    .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().toLocalDate().equals(d))
                    .mapToDouble(p -> "REFUND".equals(p.getKind()) ? -p.getAmount()
                            : p.getStatus() == PaymentStatus.SUCCESS ? p.getAmount() : 0)
                    .sum();
            daily.add(Map.of("date", d.toString(), "amount", Math.round(amount)));
        }

        Map<String, Long> byType = bookings.stream()
                .collect(Collectors.groupingBy(b -> b.getVehicle().getType().name(), TreeMap::new, Collectors.counting()));

        Map<String, Long> byVehicle = bookings.stream()
                .filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .collect(Collectors.groupingBy(b -> b.getVehicle().getName(), Collectors.counting()));
        List<Map<String, Object>> top = byVehicle.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .map(e -> Map.<String, Object>of("name", e.getKey(), "bookings", e.getValue()))
                .collect(Collectors.toList());

        Map<String, Long> byPlan = bookings.stream().filter(b -> b.getInsurancePlan() != null)
                .collect(Collectors.groupingBy(b -> b.getInsurancePlan().getLabel(), TreeMap::new, Collectors.counting()));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("revenue", Math.round(charges - refunds));
        m.put("refunds", Math.round(refunds));
        m.put("totalBookings", bookings.size());
        m.put("bookingsByStatus", byStatus);
        m.put("activeTrips", ongoing);
        m.put("fleetSize", fleet.size());
        m.put("cars", fleet.stream().filter(v -> v.getType() == VehicleType.CAR).count());
        m.put("bikes", fleet.stream().filter(v -> v.getType() == VehicleType.BIKE).count());
        m.put("inMaintenance", fleet.stream().filter(v -> !v.isAvailable()).count());
        m.put("utilization", fleet.isEmpty() ? 0 : Math.round(100.0 * ongoing / fleet.size()));
        m.put("customers", customerRepository.count());
        m.put("drivers", driverRepository.count());
        m.put("openAlerts", alertRepository.countByStatusNot(AlertStatus.RESOLVED));
        m.put("revenueLast7Days", daily);
        m.put("bookingsByType", byType);
        m.put("topVehicles", top);
        m.put("insuranceMix", byPlan);
        return m;
    }

    // ------------------------------------------------------------------ bookings

    @GetMapping("/bookings")
    public List<Booking> bookings(@RequestHeader(value = ADMIN_HEADER, required = false) String token) {
        authService.requireAdmin(token);
        return bookingRepository.findAllByOrderByCreatedAtDesc();
    }

    @PutMapping("/bookings/{ref}/status")
    public Booking setStatus(@RequestHeader(value = ADMIN_HEADER, required = false) String token,
                             @PathVariable String ref, @RequestBody StatusUpdate req) {
        authService.requireAdmin(token);
        return bookingService.adminSetStatus(ref, req.status());
    }

    @PutMapping("/bookings/{ref}/driver")
    public Booking assignDriver(@RequestHeader(value = ADMIN_HEADER, required = false) String token,
                                @PathVariable String ref, @RequestBody DriverAssign req) {
        authService.requireAdmin(token);
        return bookingService.adminAssignDriver(ref, req.driverId());
    }

    // ------------------------------------------------------------------ fleet

    @GetMapping("/vehicles")
    public List<Vehicle> vehicles(@RequestHeader(value = ADMIN_HEADER, required = false) String token) {
        authService.requireAdmin(token);
        return vehicleRepository.findAll();
    }

    @PutMapping("/vehicles/{id}")
    public Vehicle updateVehicle(@RequestHeader(value = ADMIN_HEADER, required = false) String token,
                                 @PathVariable Long id, @RequestBody VehicleUpdate req) {
        authService.requireAdmin(token);
        Vehicle v = vehicleRepository.findById(id).orElseThrow(() -> ApiException.notFound("Vehicle not found"));
        if (req.pricePerDay() != null) {
            if (req.pricePerDay() <= 0) throw ApiException.badRequest("Price must be positive");
            v.setPricePerDay(req.pricePerDay());
        }
        if (req.pricePerHour() != null) {
            if (req.pricePerHour() <= 0) throw ApiException.badRequest("Price must be positive");
            v.setPricePerHour(req.pricePerHour());
        }
        if (req.available() != null) v.setAvailable(req.available());
        if (req.imageUrl() != null) v.setImageUrl(req.imageUrl().isBlank() ? null : req.imageUrl().trim());
        return vehicleRepository.save(v);
    }

    @PostMapping("/vehicles")
    public Vehicle addVehicle(@RequestHeader(value = ADMIN_HEADER, required = false) String token,
                              @RequestBody Vehicle v) {
        authService.requireAdmin(token);
        if (v.getName() == null || v.getName().isBlank() || v.getType() == null || v.getPricePerDay() <= 0) {
            throw ApiException.badRequest("Name, type and price per day are required");
        }
        v.setId(null);
        if (v.getPricePerHour() <= 0) v.setPricePerHour(Math.round(v.getPricePerDay() / 8));
        if (v.getKmLimitPerDay() <= 0) v.setKmLimitPerDay(v.getType() == VehicleType.CAR ? 300 : 150);
        if (v.getExtraKmCharge() <= 0) v.setExtraKmCharge(v.getType() == VehicleType.CAR ? 10 : 4);
        if (v.getRating() <= 0) v.setRating(4.5);
        if (v.getColor() == null) v.setColor("#4f46e5");
        if (v.getCategory() == null) v.setCategory(v.getType() == VehicleType.CAR ? "Hatchback" : "Commuter");
        if (v.getFuelType() == null) v.setFuelType("Petrol");
        if (v.getTransmission() == null) v.setTransmission("Manual");
        if (v.getCity() == null) v.setCity("Hyderabad");
        v.setAvailable(true);
        return vehicleRepository.save(v);
    }

    // ------------------------------------------------------------------ people

    @GetMapping("/drivers")
    public List<Driver> drivers(@RequestHeader(value = ADMIN_HEADER, required = false) String token) {
        authService.requireAdmin(token);
        return driverRepository.findAll();
    }

    @PostMapping("/drivers")
    public Driver addDriver(@RequestHeader(value = ADMIN_HEADER, required = false) String token,
                            @RequestBody Driver d) {
        authService.requireAdmin(token);
        if (d.getName() == null || d.getName().isBlank()) {
            throw ApiException.badRequest("Driver name is required");
        }
        AuthService.validatePhone(d.getPhone());
        d.setId(null);
        d.setPhone(AuthService.normalizePhone(d.getPhone()));
        if (d.getRating() <= 0) d.setRating(4.5);
        if (d.getDailyCharge() <= 0) d.setDailyCharge(799);
        d.setAvailable(true);
        return driverRepository.save(d);
    }

    @PutMapping("/drivers/{id}/availability")
    public Driver driverAvailability(@RequestHeader(value = ADMIN_HEADER, required = false) String token,
                                     @PathVariable Long id, @RequestBody Map<String, Boolean> body) {
        authService.requireAdmin(token);
        Driver d = driverRepository.findById(id).orElseThrow(() -> ApiException.notFound("Driver not found"));
        d.setAvailable(Boolean.TRUE.equals(body.get("available")));
        return driverRepository.save(d);
    }

    @GetMapping("/customers")
    public List<Customer> customers(@RequestHeader(value = ADMIN_HEADER, required = false) String token) {
        authService.requireAdmin(token);
        return customerRepository.findAll();
    }

    @GetMapping("/payments")
    public List<Payment> payments(@RequestHeader(value = ADMIN_HEADER, required = false) String token) {
        authService.requireAdmin(token);
        return paymentRepository.findAllByOrderByCreatedAtDesc();
    }

    // ------------------------------------------------------------------ alerts

    @GetMapping("/alerts")
    public List<Alert> alerts(@RequestHeader(value = ADMIN_HEADER, required = false) String token) {
        authService.requireAdmin(token);
        return alertService.all();
    }

    @PutMapping("/alerts/{id}")
    public Alert updateAlert(@RequestHeader(value = ADMIN_HEADER, required = false) String token,
                             @PathVariable Long id, @RequestBody ResolveRequest req) {
        authService.requireAdmin(token);
        return alertService.update(id, req.status(), req.note());
    }
}
