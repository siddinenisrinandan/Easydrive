package com.driveease.web;

import com.driveease.dto.Dtos.Quote;
import com.driveease.dto.Dtos.QuoteRequest;
import com.driveease.model.InsurancePlan;
import com.driveease.model.Vehicle;
import com.driveease.model.VehicleType;
import com.driveease.repository.VehicleRepository;
import com.driveease.service.BookingService;
import com.driveease.service.Catalog;
import com.driveease.service.PricingService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Endpoints that work without login: fleet, filters, meta data and price quotes. */
@RestController
@RequestMapping("/api")
public class PublicController {

    private final VehicleRepository vehicleRepository;
    private final PricingService pricingService;
    private final BookingService bookingService;

    public PublicController(VehicleRepository vehicleRepository, PricingService pricingService,
                            BookingService bookingService) {
        this.vehicleRepository = vehicleRepository;
        this.pricingService = pricingService;
        this.bookingService = bookingService;
    }

    @GetMapping("/vehicles")
    public List<Vehicle> vehicles(@RequestParam(required = false) String type,
                                  @RequestParam(required = false) String category,
                                  @RequestParam(required = false) String fuel,
                                  @RequestParam(required = false) String transmission,
                                  @RequestParam(required = false) Integer seats,
                                  @RequestParam(required = false) Double maxPrice,
                                  @RequestParam(required = false) String q,
                                  @RequestParam(required = false, defaultValue = "popular") String sort,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        Comparator<Vehicle> order = switch (sort) {
            case "priceAsc" -> Comparator.comparingDouble(Vehicle::getPricePerDay);
            case "priceDesc" -> Comparator.comparingDouble(Vehicle::getPricePerDay).reversed();
            case "rating" -> Comparator.comparingDouble(Vehicle::getRating).reversed();
            default -> Comparator.comparingInt(Vehicle::getTripsCompleted).reversed();
        };
        boolean checkDates = start != null && end != null && end.isAfter(start);
        return vehicleRepository.findAll().stream()
                .filter(v -> type == null || type.isBlank() || v.getType().name().equalsIgnoreCase(type))
                .filter(v -> category == null || category.isBlank() || v.getCategory().equalsIgnoreCase(category))
                .filter(v -> fuel == null || fuel.isBlank() || v.getFuelType().equalsIgnoreCase(fuel))
                .filter(v -> transmission == null || transmission.isBlank() || v.getTransmission().equalsIgnoreCase(transmission))
                .filter(v -> seats == null || v.getSeats() >= seats)
                .filter(v -> maxPrice == null || v.getPricePerDay() <= maxPrice)
                .filter(v -> query.isEmpty() || (v.getName() + " " + v.getCategory() + " " + v.getFeatures())
                        .toLowerCase(Locale.ROOT).contains(query))
                .filter(v -> !checkDates || (v.isAvailable() && bookingService.isVehicleFree(v.getId(), start, end)))
                .sorted(order)
                .collect(Collectors.toList());
    }

    @GetMapping("/vehicles/{id}")
    public Vehicle vehicle(@PathVariable Long id) {
        return vehicleRepository.findById(id).orElseThrow(() -> ApiException.notFound("Vehicle not found"));
    }

    @GetMapping("/vehicles/{id}/availability")
    public Map<String, Object> availability(@PathVariable Long id,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
                                            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        Vehicle v = vehicle(id);
        boolean free = v.isAvailable() && end.isAfter(start) && bookingService.isVehicleFree(id, start, end);
        String msg = !v.isAvailable() ? "Under maintenance" : free ? "Available for your dates" : "Already booked for these dates";
        return Map.of("available", free, "message", msg);
    }

    @PostMapping("/pricing/quote")
    public Quote quote(@RequestBody QuoteRequest req) {
        return pricingService.quote(req);
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("locations", Catalog.LOCATIONS);
        m.put("addons", Catalog.ADDONS);
        m.put("coupons", Catalog.COUPONS);
        m.put("banks", Catalog.BANKS);
        m.put("wallets", Catalog.WALLETS);
        m.put("driverPerDay", Catalog.DRIVER_PER_DAY);
        m.put("minRentalHours", Catalog.MIN_RENTAL_HOURS);
        List<Map<String, Object>> plans = new ArrayList<>();
        for (InsurancePlan p : InsurancePlan.values()) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("code", p.name());
            pm.put("label", p.getLabel());
            pm.put("carPerDay", p.getCarPerDay());
            pm.put("bikePerDay", p.getBikePerDay());
            pm.put("carDeductible", p.deductibleFor(VehicleType.CAR));
            pm.put("bikeDeductible", p.deductibleFor(VehicleType.BIKE));
            pm.put("description", p.getDescription());
            plans.add(pm);
        }
        m.put("insurancePlans", plans);
        Set<String> categories = vehicleRepository.findAll().stream().map(Vehicle::getCategory)
                .collect(Collectors.toCollection(TreeSet::new));
        m.put("categories", categories);
        return m;
    }
}
