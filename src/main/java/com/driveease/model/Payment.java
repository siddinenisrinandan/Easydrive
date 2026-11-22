package com.driveease.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** One money movement: a charge, or a refund (kind = REFUND). */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false)
    private Booking booking;

    private String bookingRef;
    private String customerName;

    /** CHARGE or REFUND. */
    private String kind;

    @Enumerated(EnumType.STRING)
    private PaymentMethodType method;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private double amount;
    private String transactionId;

    /** Masked instrument, e.g. "VISA **** 4242" or "ra***@okhdfc". Full card numbers are never stored. */
    private String maskedDetails;
    private String gatewayMessage;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Booking getBooking() { return booking; }
    public void setBooking(Booking booking) { this.booking = booking; }
    public String getBookingRef() { return bookingRef; }
    public void setBookingRef(String bookingRef) { this.bookingRef = bookingRef; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public PaymentMethodType getMethod() { return method; }
    public void setMethod(PaymentMethodType method) { this.method = method; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getMaskedDetails() { return maskedDetails; }
    public void setMaskedDetails(String maskedDetails) { this.maskedDetails = maskedDetails; }
    public String getGatewayMessage() { return gatewayMessage; }
    public void setGatewayMessage(String gatewayMessage) { this.gatewayMessage = gatewayMessage; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
