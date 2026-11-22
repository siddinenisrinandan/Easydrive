package com.driveease.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String bookingRef;

    @ManyToOne(optional = false)
    private Customer customer;

    @ManyToOne(optional = false)
    private Vehicle vehicle;

    @ManyToOne
    private Driver driver;

    private boolean withDriver;

    // ----- trip -----
    private String pickupLocation;
    private double pickupLat;
    private double pickupLng;
    private String dropLocation;
    private double dropLat;
    private double dropLng;
    private LocalDateTime startTime;
    private LocalDateTime endTime;

    @Enumerated(EnumType.STRING)
    private InsurancePlan insurancePlan;

    /** Comma separated add-on codes, e.g. "GPS,CHILD_SEAT". */
    private String addons;
    private String couponCode;

    @Column(length = 500)
    private String specialRequests;

    // ----- price breakdown (frozen at booking time) -----
    private long totalHours;
    private int billableDays;
    private double baseAmount;
    private double weekendSurcharge;
    private double longRentalDiscount;
    private double insuranceAmount;
    private double driverAmount;
    private double addonsAmount;
    private double oneWayFee;
    private double couponDiscount;
    private double taxAmount;
    private double totalAmount;
    private double depositAmount;
    private int kmLimit;
    private double extraKmRate;

    // ----- settlement -----
    private double distanceKm;
    private double extraKmCharge;
    private double lateFee;
    private double finalAmount;
    private double refundAmount;

    @Enumerated(EnumType.STRING)
    private BookingStatus status;

    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus;

    @Enumerated(EnumType.STRING)
    private PaymentMethodType paymentMethod;

    /** Shown to the customer; verified at pickup before the trip can start. */
    private String tripOtp;

    /** Secret code for the "share live trip with family" link. */
    private String shareCode;

    // ----- live tracking -----
    private double tripProgress;
    private Double currentLat;
    private Double currentLng;
    private double currentSpeed;

    @JsonIgnore
    private double routeCtrlLat;
    @JsonIgnore
    private double routeCtrlLng;
    private double routeLengthKm;

    private LocalDateTime createdAt;
    private LocalDateTime tripStartedAt;
    private LocalDateTime tripEndedAt;
    private LocalDateTime cancelledAt;

    private Integer rating;
    @Column(length = 1000)
    private String review;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getBookingRef() { return bookingRef; }
    public void setBookingRef(String bookingRef) { this.bookingRef = bookingRef; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public Vehicle getVehicle() { return vehicle; }
    public void setVehicle(Vehicle vehicle) { this.vehicle = vehicle; }
    public Driver getDriver() { return driver; }
    public void setDriver(Driver driver) { this.driver = driver; }
    public boolean isWithDriver() { return withDriver; }
    public void setWithDriver(boolean withDriver) { this.withDriver = withDriver; }
    public String getPickupLocation() { return pickupLocation; }
    public void setPickupLocation(String pickupLocation) { this.pickupLocation = pickupLocation; }
    public double getPickupLat() { return pickupLat; }
    public void setPickupLat(double pickupLat) { this.pickupLat = pickupLat; }
    public double getPickupLng() { return pickupLng; }
    public void setPickupLng(double pickupLng) { this.pickupLng = pickupLng; }
    public String getDropLocation() { return dropLocation; }
    public void setDropLocation(String dropLocation) { this.dropLocation = dropLocation; }
    public double getDropLat() { return dropLat; }
    public void setDropLat(double dropLat) { this.dropLat = dropLat; }
    public double getDropLng() { return dropLng; }
    public void setDropLng(double dropLng) { this.dropLng = dropLng; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public InsurancePlan getInsurancePlan() { return insurancePlan; }
    public void setInsurancePlan(InsurancePlan insurancePlan) { this.insurancePlan = insurancePlan; }
    public String getAddons() { return addons; }
    public void setAddons(String addons) { this.addons = addons; }
    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }
    public String getSpecialRequests() { return specialRequests; }
    public void setSpecialRequests(String specialRequests) { this.specialRequests = specialRequests; }
    public long getTotalHours() { return totalHours; }
    public void setTotalHours(long totalHours) { this.totalHours = totalHours; }
    public int getBillableDays() { return billableDays; }
    public void setBillableDays(int billableDays) { this.billableDays = billableDays; }
    public double getBaseAmount() { return baseAmount; }
    public void setBaseAmount(double baseAmount) { this.baseAmount = baseAmount; }
    public double getWeekendSurcharge() { return weekendSurcharge; }
    public void setWeekendSurcharge(double weekendSurcharge) { this.weekendSurcharge = weekendSurcharge; }
    public double getLongRentalDiscount() { return longRentalDiscount; }
    public void setLongRentalDiscount(double longRentalDiscount) { this.longRentalDiscount = longRentalDiscount; }
    public double getInsuranceAmount() { return insuranceAmount; }
    public void setInsuranceAmount(double insuranceAmount) { this.insuranceAmount = insuranceAmount; }
    public double getDriverAmount() { return driverAmount; }
    public void setDriverAmount(double driverAmount) { this.driverAmount = driverAmount; }
    public double getAddonsAmount() { return addonsAmount; }
    public void setAddonsAmount(double addonsAmount) { this.addonsAmount = addonsAmount; }
    public double getOneWayFee() { return oneWayFee; }
    public void setOneWayFee(double oneWayFee) { this.oneWayFee = oneWayFee; }
    public double getCouponDiscount() { return couponDiscount; }
    public void setCouponDiscount(double couponDiscount) { this.couponDiscount = couponDiscount; }
    public double getTaxAmount() { return taxAmount; }
    public void setTaxAmount(double taxAmount) { this.taxAmount = taxAmount; }
    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
    public double getDepositAmount() { return depositAmount; }
    public void setDepositAmount(double depositAmount) { this.depositAmount = depositAmount; }
    public int getKmLimit() { return kmLimit; }
    public void setKmLimit(int kmLimit) { this.kmLimit = kmLimit; }
    public double getExtraKmRate() { return extraKmRate; }
    public void setExtraKmRate(double extraKmRate) { this.extraKmRate = extraKmRate; }
    public double getDistanceKm() { return distanceKm; }
    public void setDistanceKm(double distanceKm) { this.distanceKm = distanceKm; }
    public double getExtraKmCharge() { return extraKmCharge; }
    public void setExtraKmCharge(double extraKmCharge) { this.extraKmCharge = extraKmCharge; }
    public double getLateFee() { return lateFee; }
    public void setLateFee(double lateFee) { this.lateFee = lateFee; }
    public double getFinalAmount() { return finalAmount; }
    public void setFinalAmount(double finalAmount) { this.finalAmount = finalAmount; }
    public double getRefundAmount() { return refundAmount; }
    public void setRefundAmount(double refundAmount) { this.refundAmount = refundAmount; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public void setPaymentStatus(PaymentStatus paymentStatus) { this.paymentStatus = paymentStatus; }
    public PaymentMethodType getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethodType paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getTripOtp() { return tripOtp; }
    public void setTripOtp(String tripOtp) { this.tripOtp = tripOtp; }
    public String getShareCode() { return shareCode; }
    public void setShareCode(String shareCode) { this.shareCode = shareCode; }
    public double getTripProgress() { return tripProgress; }
    public void setTripProgress(double tripProgress) { this.tripProgress = tripProgress; }
    public Double getCurrentLat() { return currentLat; }
    public void setCurrentLat(Double currentLat) { this.currentLat = currentLat; }
    public Double getCurrentLng() { return currentLng; }
    public void setCurrentLng(Double currentLng) { this.currentLng = currentLng; }
    public double getCurrentSpeed() { return currentSpeed; }
    public void setCurrentSpeed(double currentSpeed) { this.currentSpeed = currentSpeed; }
    public double getRouteCtrlLat() { return routeCtrlLat; }
    public void setRouteCtrlLat(double routeCtrlLat) { this.routeCtrlLat = routeCtrlLat; }
    public double getRouteCtrlLng() { return routeCtrlLng; }
    public void setRouteCtrlLng(double routeCtrlLng) { this.routeCtrlLng = routeCtrlLng; }
    public double getRouteLengthKm() { return routeLengthKm; }
    public void setRouteLengthKm(double routeLengthKm) { this.routeLengthKm = routeLengthKm; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getTripStartedAt() { return tripStartedAt; }
    public void setTripStartedAt(LocalDateTime tripStartedAt) { this.tripStartedAt = tripStartedAt; }
    public LocalDateTime getTripEndedAt() { return tripEndedAt; }
    public void setTripEndedAt(LocalDateTime tripEndedAt) { this.tripEndedAt = tripEndedAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public void setCancelledAt(LocalDateTime cancelledAt) { this.cancelledAt = cancelledAt; }
    public Integer getRating() { return rating; }
    public void setRating(Integer rating) { this.rating = rating; }
    public String getReview() { return review; }
    public void setReview(String review) { this.review = review; }
}
