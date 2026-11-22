package com.driveease.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** One GPS ping from the vehicle telematics unit (simulated in this project). */
@Entity
@Table(name = "tracking_points")
public class TrackingPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long bookingId;
    private double lat;
    private double lng;
    private double speed;
    private LocalDateTime recordedAt;

    public TrackingPoint() {
    }

    public TrackingPoint(Long bookingId, double lat, double lng, double speed, LocalDateTime recordedAt) {
        this.bookingId = bookingId;
        this.lat = lat;
        this.lng = lng;
        this.speed = speed;
        this.recordedAt = recordedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getBookingId() { return bookingId; }
    public void setBookingId(Long bookingId) { this.bookingId = bookingId; }
    public double getLat() { return lat; }
    public void setLat(double lat) { this.lat = lat; }
    public double getLng() { return lng; }
    public void setLng(double lng) { this.lng = lng; }
    public double getSpeed() { return speed; }
    public void setSpeed(double speed) { this.speed = speed; }
    public LocalDateTime getRecordedAt() { return recordedAt; }
    public void setRecordedAt(LocalDateTime recordedAt) { this.recordedAt = recordedAt; }
}
