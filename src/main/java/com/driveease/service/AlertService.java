package com.driveease.service;

import com.driveease.dto.Dtos.AlertRequest;
import com.driveease.model.*;
import com.driveease.repository.AlertRepository;
import com.driveease.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** SOS, accident, breakdown, medical and support requests during a trip. */
@Service
public class AlertService {

    /** Emergency numbers shown to the customer after raising an SOS (India). */
    public static final Map<String, String> EMERGENCY_NUMBERS = Map.of(
            "National Emergency", "112",
            "Ambulance", "108",
            "Police", "100",
            "Women Helpline", "181",
            "DriveEase 24x7 Roadside Assistance", "1800-123-4567");

    private final AlertRepository alertRepository;

    public AlertService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    static Alert baseAlert(Booking b) {
        Alert a = new Alert();
        a.setBookingRef(b.getBookingRef());
        a.setStatus(AlertStatus.OPEN);
        a.setCustomerName(b.getCustomer().getFullName());
        a.setCustomerPhone(b.getCustomer().getPhone());
        a.setVehicleName(b.getVehicle().getName() + " (" + b.getVehicle().getRegistrationNumber() + ")");
        if (b.getDriver() != null) {
            a.setDriverName(b.getDriver().getName());
            a.setDriverPhone(b.getDriver().getPhone());
        }
        a.setCreatedAt(LocalDateTime.now());
        return a;
    }

    @Transactional
    public Alert raise(Booking b, AlertRequest req) {
        if (b.getStatus() == BookingStatus.CANCELLED) {
            throw ApiException.badRequest("This booking is cancelled");
        }
        AlertType type;
        try {
            type = AlertType.valueOf(req.type() == null ? "SUPPORT" : req.type().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Unknown alert type");
        }
        if (type == AlertType.OVERSPEED) {
            throw ApiException.badRequest("Unknown alert type");
        }
        Alert a = baseAlert(b);
        a.setType(type);
        a.setRaisedBy("CUSTOMER");
        String msg = req.message() == null || req.message().isBlank() ? defaultMessage(type) : req.message().trim();
        a.setMessage(msg.length() > 990 ? msg.substring(0, 990) : msg);
        a.setLat(req.lat() != null ? req.lat() : b.getCurrentLat() != null ? b.getCurrentLat() : b.getPickupLat());
        a.setLng(req.lng() != null ? req.lng() : b.getCurrentLng() != null ? b.getCurrentLng() : b.getPickupLng());
        if (type == AlertType.SOS || type == AlertType.ACCIDENT || type == AlertType.MEDICAL) {
            a.setStatus(AlertStatus.IN_PROGRESS); // escalated immediately to the response team
        }
        return alertRepository.save(a);
    }

    public List<Alert> forBooking(Booking b) {
        return alertRepository.findByBookingRefOrderByCreatedAtDesc(b.getBookingRef());
    }

    public List<Alert> all() {
        return alertRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public Alert update(Long id, String status, String note) {
        Alert a = alertRepository.findById(id).orElseThrow(() -> ApiException.notFound("Alert not found"));
        AlertStatus s = status == null ? AlertStatus.RESOLVED : AlertStatus.valueOf(status.toUpperCase(Locale.ROOT));
        a.setStatus(s);
        if (note != null && !note.isBlank()) {
            a.setResolutionNote(note.trim());
        }
        if (s == AlertStatus.RESOLVED) {
            a.setResolvedAt(LocalDateTime.now());
        }
        return alertRepository.save(a);
    }

    public static String responseText(AlertType type) {
        return switch (type) {
            case SOS -> "SOS received! Our emergency response team has your live location and will call you within 60 seconds. If you are in danger, dial 112 now.";
            case ACCIDENT -> "Accident reported. Help is being dispatched to your live location. Dial 108 for an ambulance if anyone is hurt. Do not move the vehicle.";
            case MEDICAL -> "Medical emergency reported. Dial 108 for an ambulance. Our team is calling you and sharing your location with the nearest hospital.";
            case BREAKDOWN -> "Breakdown reported. A roadside assistance van is being assigned - expected arrival 30-45 minutes. Turn on hazard lights and stay safe.";
            case SUPPORT -> "Support ticket created. Our customer care team will call you shortly.";
            case OVERSPEED -> "Please slow down.";
        };
    }

    private static String defaultMessage(AlertType type) {
        return switch (type) {
            case SOS -> "Emergency SOS triggered by customer";
            case ACCIDENT -> "Customer reported an accident";
            case MEDICAL -> "Customer reported a medical emergency";
            case BREAKDOWN -> "Vehicle breakdown reported";
            default -> "Customer needs help";
        };
    }
}
