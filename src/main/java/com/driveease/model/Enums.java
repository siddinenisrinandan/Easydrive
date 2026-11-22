package com.driveease.model;

/**
 * Shared limits used by the domain model.
 */
public final class Enums {

    /** Speed above which an automatic OVERSPEED alert is raised (km/h). */
    public static final double CAR_SPEED_LIMIT = 90;
    public static final double BIKE_SPEED_LIMIT = 70;

    private Enums() {
    }

    public static double speedLimitFor(VehicleType type) {
        return type == VehicleType.CAR ? CAR_SPEED_LIMIT : BIKE_SPEED_LIMIT;
    }
}
