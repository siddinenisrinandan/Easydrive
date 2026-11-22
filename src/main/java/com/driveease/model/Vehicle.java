package com.driveease.model;

import jakarta.persistence.*;

@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String brand;

    @Enumerated(EnumType.STRING)
    private VehicleType type;

    /** Hatchback, Sedan, SUV, MUV, EV, Luxury, Off-Road, Scooter, Cruiser, Sports, Adventure, Commuter ... */
    private String category;
    private String fuelType;
    private String transmission;
    private int seats;
    private int modelYear;
    private String engine;
    private String mileage;

    private double pricePerDay;
    private double pricePerHour;
    private double deposit;
    private int kmLimitPerDay;
    private double extraKmCharge;

    /** Hex color used to render the vehicle illustration when no photo is set. */
    private String color;
    private String imageUrl;

    private double rating;
    private int tripsCompleted;

    @Column(length = 600)
    private String features;

    @Column(length = 1200)
    private String description;

    private String registrationNumber;
    private String city;
    private boolean available = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public VehicleType getType() { return type; }
    public void setType(VehicleType type) { this.type = type; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getFuelType() { return fuelType; }
    public void setFuelType(String fuelType) { this.fuelType = fuelType; }
    public String getTransmission() { return transmission; }
    public void setTransmission(String transmission) { this.transmission = transmission; }
    public int getSeats() { return seats; }
    public void setSeats(int seats) { this.seats = seats; }
    public int getModelYear() { return modelYear; }
    public void setModelYear(int modelYear) { this.modelYear = modelYear; }
    public String getEngine() { return engine; }
    public void setEngine(String engine) { this.engine = engine; }
    public String getMileage() { return mileage; }
    public void setMileage(String mileage) { this.mileage = mileage; }
    public double getPricePerDay() { return pricePerDay; }
    public void setPricePerDay(double pricePerDay) { this.pricePerDay = pricePerDay; }
    public double getPricePerHour() { return pricePerHour; }
    public void setPricePerHour(double pricePerHour) { this.pricePerHour = pricePerHour; }
    public double getDeposit() { return deposit; }
    public void setDeposit(double deposit) { this.deposit = deposit; }
    public int getKmLimitPerDay() { return kmLimitPerDay; }
    public void setKmLimitPerDay(int kmLimitPerDay) { this.kmLimitPerDay = kmLimitPerDay; }
    public double getExtraKmCharge() { return extraKmCharge; }
    public void setExtraKmCharge(double extraKmCharge) { this.extraKmCharge = extraKmCharge; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }
    public int getTripsCompleted() { return tripsCompleted; }
    public void setTripsCompleted(int tripsCompleted) { this.tripsCompleted = tripsCompleted; }
    public String getFeatures() { return features; }
    public void setFeatures(String features) { this.features = features; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getRegistrationNumber() { return registrationNumber; }
    public void setRegistrationNumber(String registrationNumber) { this.registrationNumber = registrationNumber; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
}
