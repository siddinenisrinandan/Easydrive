package com.driveease.model;

import jakarta.persistence.*;

/** Professional chauffeur that can be added to a car booking. */
@Entity
@Table(name = "drivers")
public class Driver {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String phone;
    private String email;
    private String licenseNumber;
    private int experienceYears;
    private double rating;
    private String languages;
    private String city;
    private double dailyCharge;
    private int tripsCompleted;
    private boolean available = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getLicenseNumber() { return licenseNumber; }
    public void setLicenseNumber(String licenseNumber) { this.licenseNumber = licenseNumber; }
    public int getExperienceYears() { return experienceYears; }
    public void setExperienceYears(int experienceYears) { this.experienceYears = experienceYears; }
    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }
    public String getLanguages() { return languages; }
    public void setLanguages(String languages) { this.languages = languages; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public double getDailyCharge() { return dailyCharge; }
    public void setDailyCharge(double dailyCharge) { this.dailyCharge = dailyCharge; }
    public int getTripsCompleted() { return tripsCompleted; }
    public void setTripsCompleted(int tripsCompleted) { this.tripsCompleted = tripsCompleted; }
    public boolean isAvailable() { return available; }
    public void setAvailable(boolean available) { this.available = available; }
}
