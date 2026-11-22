package com.driveease.model;

/** Booking life cycle: PENDING_PAYMENT -> CONFIRMED -> ONGOING -> COMPLETED (or CANCELLED). */
public enum BookingStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    ONGOING,
    COMPLETED,
    CANCELLED
}
