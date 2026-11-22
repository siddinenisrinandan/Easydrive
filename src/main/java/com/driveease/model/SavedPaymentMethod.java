package com.driveease.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * A payment instrument the customer chose to save.
 * Like real gateways (Razorpay / Stripe) we keep only a token plus masked details:
 * never the full card number and never the CVV.
 */
@Entity
@Table(name = "saved_payment_methods")
public class SavedPaymentMethod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(optional = false)
    private Customer customer;

    @Enumerated(EnumType.STRING)
    private PaymentMethodType type;

    private String label;
    private String maskedValue;
    private String brand;
    private String expiry;
    private String holderName;

    /** Opaque gateway token that stands in for the real instrument. */
    @JsonIgnore
    private String gatewayToken;

    private boolean defaultMethod;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Customer getCustomer() { return customer; }
    public void setCustomer(Customer customer) { this.customer = customer; }
    public PaymentMethodType getType() { return type; }
    public void setType(PaymentMethodType type) { this.type = type; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getMaskedValue() { return maskedValue; }
    public void setMaskedValue(String maskedValue) { this.maskedValue = maskedValue; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getExpiry() { return expiry; }
    public void setExpiry(String expiry) { this.expiry = expiry; }
    public String getHolderName() { return holderName; }
    public void setHolderName(String holderName) { this.holderName = holderName; }
    public String getGatewayToken() { return gatewayToken; }
    public void setGatewayToken(String gatewayToken) { this.gatewayToken = gatewayToken; }
    public boolean isDefaultMethod() { return defaultMethod; }
    public void setDefaultMethod(boolean defaultMethod) { this.defaultMethod = defaultMethod; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
