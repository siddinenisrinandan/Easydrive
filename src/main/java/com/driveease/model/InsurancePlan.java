package com.driveease.model;

/**
 * Trip protection plans. Prices are per rental day.
 * The deductible is the maximum amount the customer pays if the vehicle is damaged.
 */
public enum InsurancePlan {

    BASIC("Basic Cover", 0, 0, 25000,
            "Third-party liability as required by law. You pay damages up to the deductible."),
    STANDARD("Standard Protection", 199, 79, 5000,
            "Own-damage cover, theft protection and 24x7 roadside assistance. Low deductible."),
    PREMIUM("Premium Zero-Dep", 399, 149, 0,
            "Zero depreciation, zero deductible, tyre & glass, personal accident cover and 50% lower security deposit.");

    private final String label;
    private final double carPerDay;
    private final double bikePerDay;
    private final double deductible;
    private final String description;

    InsurancePlan(String label, double carPerDay, double bikePerDay, double deductible, String description) {
        this.label = label;
        this.carPerDay = carPerDay;
        this.bikePerDay = bikePerDay;
        this.deductible = deductible;
        this.description = description;
    }

    public String getLabel() { return label; }
    public double getCarPerDay() { return carPerDay; }
    public double getBikePerDay() { return bikePerDay; }
    public double getDeductible() { return deductible; }
    public String getDescription() { return description; }

    public double perDayFor(VehicleType type) {
        return type == VehicleType.CAR ? carPerDay : bikePerDay;
    }

    /** Deductible for bikes is 40% of the car deductible. */
    public double deductibleFor(VehicleType type) {
        return type == VehicleType.CAR ? deductible : Math.round(deductible * 0.4);
    }
}
