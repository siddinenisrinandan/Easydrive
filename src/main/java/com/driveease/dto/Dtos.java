package com.driveease.dto;

import com.driveease.model.Alert;
import com.driveease.model.Customer;
import com.driveease.model.Vehicle;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

/** Request / response objects of the REST API (Java records -> JSON). */
public final class Dtos {

    private Dtos() {
    }

    // ---------------------------------------------------------------- pricing

    public record QuoteRequest(Long vehicleId, LocalDateTime startTime, LocalDateTime endTime,
                               String insurancePlan, boolean withDriver, List<String> addons,
                               String couponCode, String pickupLocation, String dropLocation) {
    }

    public record QuoteLine(String label, double amount, String note) {
    }

    public record Quote(Long vehicleId, String vehicleName, long totalHours, int billableDays,
                        List<QuoteLine> lines,
                        double baseAmount, double weekendSurcharge, double longRentalDiscount,
                        double insuranceAmount, double driverAmount, double addonsAmount, double oneWayFee,
                        double couponDiscount, boolean couponApplied, String couponMessage,
                        double subtotal, double taxAmount, double totalAmount, double depositAmount,
                        int kmLimit, double extraKmRate, double deductible,
                        String insurancePlan, boolean withDriver, List<String> addons) {
    }

    // ---------------------------------------------------------------- auth

    public record RegisterRequest(@NotBlank String fullName,
                                  @NotBlank @Email String email,
                                  @NotBlank String phone,
                                  @NotBlank @Size(min = 6, message = "must be at least 6 characters") String password) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record AuthResponse(String token, Customer customer) {
    }

    public record ProfileUpdate(String fullName, String phone, String dateOfBirth, String licenseNumber,
                                String licenseExpiry, String address, String city,
                                String emergencyContactName, String emergencyContactPhone) {
    }

    // ---------------------------------------------------------------- booking

    public record CreateBookingRequest(Long vehicleId, LocalDateTime startTime, LocalDateTime endTime,
                                       String pickupLocation, String dropLocation,
                                       String insurancePlan, boolean withDriver, List<String> addons,
                                       String couponCode, String specialRequests,
                                       // customer details captured during checkout
                                       String fullName, String phone, String dateOfBirth,
                                       String licenseNumber, String licenseExpiry, String address,
                                       String emergencyContactName, String emergencyContactPhone) {
    }

    public record PaymentRequest(String method, Long savedMethodId,
                                 String upiId,
                                 String cardNumber, String cardHolder, String cardExpiry, String cardCvv,
                                 String bank,
                                 String walletProvider, String walletPhone,
                                 boolean saveMethod) {
    }

    public record StartTripRequest(String otp) {
    }

    public record ReviewRequest(int rating, String comment) {
    }

    public record AlertRequest(String type, String message, Double lat, Double lng) {
    }

    // ---------------------------------------------------------------- tracking

    public record PersonInfo(String name, String phone, String email, String detail,
                             String emergencyName, String emergencyPhone, Double rating) {
    }

    public record TrackingView(String bookingRef, String status, String vehicleName, String vehicleType,
                               String registrationNumber, String vehicleColor,
                               String pickupLocation, double pickupLat, double pickupLng,
                               String dropLocation, double dropLat, double dropLng,
                               Double currentLat, Double currentLng, double currentSpeed, double speedLimit,
                               double progress, double distanceTravelledKm, double routeLengthKm,
                               double remainingKm, Integer etaMinutes,
                               List<double[]> plannedRoute, List<double[]> trail,
                               PersonInfo customer, PersonInfo driver, List<Alert> alerts,
                               LocalDateTime tripStartedAt, LocalDateTime endTime, int kmLimit,
                               boolean sharedView, String shareCode) {
    }

    // ---------------------------------------------------------------- AI

    public record ChatTurn(String role, String text) {
    }

    public record ChatRequest(String message, List<ChatTurn> history) {
    }

    public record ChatResponse(String reply, List<Vehicle> vehicles, List<String> quickReplies, String source) {
    }

    // ---------------------------------------------------------------- admin

    public record AdminLoginRequest(String username, String password) {
    }

    public record StatusUpdate(String status) {
    }

    public record DriverAssign(Long driverId) {
    }

    public record VehicleUpdate(Double pricePerDay, Double pricePerHour, Boolean available, String imageUrl) {
    }

    public record ResolveRequest(String status, String note) {
    }
}
