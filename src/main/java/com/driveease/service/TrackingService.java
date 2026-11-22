package com.driveease.service;

import com.driveease.dto.Dtos.PersonInfo;
import com.driveease.dto.Dtos.TrackingView;
import com.driveease.model.*;
import com.driveease.repository.AlertRepository;
import com.driveease.repository.BookingRepository;
import com.driveease.repository.TrackingPointRepository;
import com.driveease.util.Geo;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Live GPS tracking.
 *
 * In a real fleet each vehicle has a telematics device that posts GPS pings.
 * Here a scheduler simulates that device: every 3 seconds every ONGOING trip moves along a
 * smooth route from pickup to drop, its speed is recorded, and over-speeding raises an alert.
 */
@Service
public class TrackingService {

    private static final long TICK_MS = 3000;

    private final BookingRepository bookingRepository;
    private final TrackingPointRepository pointRepository;
    private final AlertRepository alertRepository;

    public TrackingService(BookingRepository bookingRepository, TrackingPointRepository pointRepository,
                           AlertRepository alertRepository) {
        this.bookingRepository = bookingRepository;
        this.pointRepository = pointRepository;
        this.alertRepository = alertRepository;
    }

    /** Builds the curved route (quadratic Bezier control point) when a trip starts. */
    public static void prepareRoute(Booking b) {
        double lat0 = b.getPickupLat(), lng0 = b.getPickupLng();
        double lat2 = b.getDropLat(), lng2 = b.getDropLng();
        double cLat, cLng;
        if (Geo.haversineKm(lat0, lng0, lat2, lng2) < 0.5) {
            // round trip: go out on a loop and come back
            cLat = lat0 + 0.045;
            cLng = lng0 + 0.06;
        } else {
            double midLat = (lat0 + lat2) / 2, midLng = (lng0 + lng2) / 2;
            double dLat = lat2 - lat0, dLng = lng2 - lng0;
            double side = ThreadLocalRandom.current().nextBoolean() ? 1 : -1;
            cLat = midLat - dLng * 0.18 * side;
            cLng = midLng + dLat * 0.18 * side;
        }
        b.setRouteCtrlLat(cLat);
        b.setRouteCtrlLng(cLng);
        b.setRouteLengthKm(Geo.round2(Geo.bezierLengthKm(lat0, lng0, cLat, cLng, lat2, lng2)));
        b.setTripProgress(0);
        b.setDistanceKm(0);
        b.setCurrentLat(lat0);
        b.setCurrentLng(lng0);
        b.setCurrentSpeed(0);
    }

    @Scheduled(fixedDelay = TICK_MS)
    @Transactional
    public void simulateTelematics() {
        List<Booking> ongoing = bookingRepository.findByStatus(BookingStatus.ONGOING);
        for (Booking b : ongoing) {
            tick(b);
        }
    }

    private void tick(Booking b) {
        // Entities are only read here; all writes go through updateTelemetry (see BookingRepository).
        if (b.getRouteLengthKm() <= 0 || b.getTripProgress() >= 1.0) {
            if (b.getCurrentSpeed() != 0) {
                bookingRepository.updateTelemetry(b.getId(), b.getTripProgress(), b.getCurrentLat(), b.getCurrentLng(),
                        0, b.getDistanceKm(), BookingStatus.ONGOING);
            }
            return;
        }
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        boolean car = b.getVehicle().getType() == VehicleType.CAR;
        double speed = car ? 35 + rnd.nextDouble() * 50 : 25 + rnd.nextDouble() * 40;
        if (rnd.nextDouble() < 0.06) {
            speed = car ? 92 + rnd.nextDouble() * 22 : 72 + rnd.nextDouble() * 15; // occasional over-speeding
        }
        speed = Geo.round2(speed);

        // time compression so a demo trip finishes in a few minutes
        double route = b.getRouteLengthKm();
        double accel = Math.max(4, Math.min(60, route / 6));
        double stepKm = speed * (TICK_MS / 3_600_000.0) * accel;
        double progress = Math.min(1.0, b.getTripProgress() + stepKm / Math.max(route, 0.1));

        double[] p = Geo.bezier(b.getPickupLat(), b.getPickupLng(), b.getRouteCtrlLat(), b.getRouteCtrlLng(),
                b.getDropLat(), b.getDropLng(), progress);
        double lat = p[0] + (progress < 1 ? (rnd.nextDouble() - 0.5) * 0.0004 : 0);
        double lng = p[1] + (progress < 1 ? (rnd.nextDouble() - 0.5) * 0.0004 : 0);

        double travelled = b.getCurrentLat() == null || b.getCurrentLng() == null ? 0
                : Geo.haversineKm(b.getCurrentLat(), b.getCurrentLng(), lat, lng);
        double distance = Geo.round2(b.getDistanceKm() + travelled);
        int updated = bookingRepository.updateTelemetry(b.getId(), progress, lat, lng,
                progress >= 1.0 ? 0 : speed, distance, BookingStatus.ONGOING);
        if (updated == 0) {
            return; // trip ended meanwhile
        }
        pointRepository.save(new TrackingPoint(b.getId(), lat, lng, speed, LocalDateTime.now()));

        double limit = Enums.speedLimitFor(b.getVehicle().getType());
        if (speed > limit) {
            long recent = alertRepository.countByBookingRefAndTypeAndCreatedAtAfter(
                    b.getBookingRef(), AlertType.OVERSPEED, LocalDateTime.now().minusSeconds(90));
            if (recent == 0) {
                Alert a = AlertService.baseAlert(b);
                a.setType(AlertType.OVERSPEED);
                a.setRaisedBy("SYSTEM");
                a.setMessage(String.format("Over-speeding detected: %.0f km/h (limit %.0f km/h). Please slow down.", speed, limit));
                a.setLat(lat);
                a.setLng(lng);
                alertRepository.save(a);
            }
        }
    }

    /** Builds the live tracking screen data. Shared views (family link) hide sensitive details. */
    public TrackingView view(Booking b, boolean sharedView) {
        List<double[]> planned = new ArrayList<>();
        boolean hasRoute = b.getRouteLengthKm() > 0;
        double cLat = hasRoute ? b.getRouteCtrlLat() : (b.getPickupLat() + b.getDropLat()) / 2;
        double cLng = hasRoute ? b.getRouteCtrlLng() : (b.getPickupLng() + b.getDropLng()) / 2;
        for (int i = 0; i <= 60; i++) {
            planned.add(Geo.bezier(b.getPickupLat(), b.getPickupLng(), cLat, cLng,
                    b.getDropLat(), b.getDropLng(), i / 60.0));
        }
        List<TrackingPoint> pts = pointRepository.findTop400ByBookingIdOrderByIdDesc(b.getId());
        List<double[]> trail = new ArrayList<>();
        trail.add(new double[]{b.getPickupLat(), b.getPickupLng()});
        List<TrackingPoint> ordered = new ArrayList<>(pts);
        Collections.reverse(ordered);
        for (TrackingPoint tp : ordered) {
            trail.add(new double[]{tp.getLat(), tp.getLng()});
        }

        double route = hasRoute ? b.getRouteLengthKm()
                : Geo.round2(Geo.bezierLengthKm(b.getPickupLat(), b.getPickupLng(), cLat, cLng, b.getDropLat(), b.getDropLng()));
        double remaining = Geo.round2(route * (1 - b.getTripProgress()));
        Integer eta = null;
        if (b.getStatus() == BookingStatus.ONGOING) {
            double avg = Math.max(b.getCurrentSpeed(), 25);
            eta = (int) Math.round(remaining / avg * 60);
        }

        Customer c = b.getCustomer();
        PersonInfo customer = sharedView
                ? new PersonInfo(firstName(c.getFullName()), null, null, null, null, null, null)
                : new PersonInfo(c.getFullName(), c.getPhone(), c.getEmail(),
                "Licence: " + nullToDash(c.getLicenseNumber()),
                c.getEmergencyContactName(), c.getEmergencyContactPhone(), null);
        PersonInfo driver = null;
        if (b.getDriver() != null) {
            Driver d = b.getDriver();
            driver = new PersonInfo(d.getName(), d.getPhone(), sharedView ? null : d.getEmail(),
                    d.getExperienceYears() + " yrs experience | " + d.getLanguages(), null, null, d.getRating());
        }
        List<Alert> alerts = sharedView ? List.of() : alertRepository.findByBookingRefOrderByCreatedAtDesc(b.getBookingRef());
        Vehicle v = b.getVehicle();

        return new TrackingView(b.getBookingRef(), b.getStatus().name(), v.getName(), v.getType().name(),
                v.getRegistrationNumber(), v.getColor(),
                b.getPickupLocation(), b.getPickupLat(), b.getPickupLng(),
                b.getDropLocation(), b.getDropLat(), b.getDropLng(),
                b.getCurrentLat(), b.getCurrentLng(), b.getCurrentSpeed(), Enums.speedLimitFor(v.getType()),
                Geo.round2(b.getTripProgress()), b.getDistanceKm(), route, remaining, eta,
                planned, trail, customer, driver, alerts,
                b.getTripStartedAt(), b.getEndTime(), b.getKmLimit(),
                sharedView, sharedView ? null : b.getShareCode());
    }

    private static String firstName(String name) {
        if (name == null || name.isBlank()) {
            return "Traveller";
        }
        return name.trim().split("\\s+")[0];
    }

    private static String nullToDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }
}
