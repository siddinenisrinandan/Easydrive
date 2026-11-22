package com.driveease.service;

import com.driveease.model.VehicleType;

import java.util.List;
import java.util.Optional;

/** Static business data: branches, add-ons, coupons, banks and wallets. */
public final class Catalog {

    private Catalog() {
    }

    public record Location(String name, String city, double lat, double lng) {
    }

    /** appliesTo: CAR, BIKE or ALL. perDay=false means a one-time fee. */
    public record Addon(String code, String name, String description, double price, boolean perDay, String appliesTo) {
        public boolean appliesTo(VehicleType type) {
            return "ALL".equals(appliesTo) || type.name().equals(appliesTo);
        }
    }

    public record Coupon(String code, String description) {
    }

    public static final double DRIVER_PER_DAY = 799;
    public static final double ONE_WAY_FEE_CAR = 499;
    public static final double ONE_WAY_FEE_BIKE = 199;
    public static final double GST_RATE = 0.18;
    public static final int MIN_RENTAL_HOURS = 4;
    public static final int MAX_RENTAL_DAYS = 90;

    public static final List<Location> LOCATIONS = List.of(
            new Location("Hitech City (Cyber Towers)", "Hyderabad", 17.4504, 78.3810),
            new Location("Gachibowli (DLF Cyber City)", "Hyderabad", 17.4401, 78.3489),
            new Location("RGIA Airport, Shamshabad", "Hyderabad", 17.2403, 78.4294),
            new Location("Secunderabad Railway Station", "Hyderabad", 17.4337, 78.5016),
            new Location("Banjara Hills Road No. 12", "Hyderabad", 17.4156, 78.4347),
            new Location("Kukatpally (KPHB)", "Hyderabad", 17.4933, 78.3996),
            new Location("Charminar", "Hyderabad", 17.3616, 78.4747),
            new Location("LB Nagar", "Hyderabad", 17.3457, 78.5522),
            new Location("Vijayawada (Benz Circle)", "Vijayawada", 16.4975, 80.6550),
            new Location("Visakhapatnam (RK Beach)", "Visakhapatnam", 17.7146, 83.3233),
            new Location("Tirupati (Railway Station)", "Tirupati", 13.6288, 79.4192),
            new Location("Bengaluru (MG Road)", "Bengaluru", 12.9757, 77.6055)
    );

    public static final List<Addon> ADDONS = List.of(
            new Addon("GPS", "GPS Navigator", "Offline maps device with lane guidance", 99, true, "ALL"),
            new Addon("CHILD_SEAT", "Child Seat", "ISOFIX baby / child safety seat", 149, true, "CAR"),
            new Addon("WIFI", "4G Wi-Fi Hotspot", "Unlimited in-car internet for 5 devices", 149, true, "CAR"),
            new Addon("FASTAG", "FASTag (Rs.500 balance)", "Pre-loaded toll tag, unused balance refunded", 500, false, "CAR"),
            new Addon("EXTRA_HELMET", "Pillion Helmet", "ISI certified helmet for the pillion rider", 49, true, "BIKE"),
            new Addon("RIDING_GEAR", "Riding Gear Kit", "Riding jacket, gloves and knee guards", 199, true, "BIKE"),
            new Addon("DOORSTEP", "Doorstep Delivery", "Vehicle delivered to and picked up from your address", 299, false, "ALL")
    );

    public static final List<Coupon> COUPONS = List.of(
            new Coupon("FIRST20", "20% off the rental (max Rs.1,000) on your first booking"),
            new Coupon("WEEKEND10", "10% off when your trip includes a Saturday or Sunday"),
            new Coupon("BIKE15", "15% off any bike or scooter (max Rs.500)"),
            new Coupon("EV25", "25% off electric vehicles (max Rs.1,500) - go green!"),
            new Coupon("LONGDRIVE", "Flat Rs.750 off on trips of 5 days or more")
    );

    public static final List<String> BANKS = List.of(
            "State Bank of India", "HDFC Bank", "ICICI Bank", "Axis Bank", "Kotak Mahindra Bank",
            "Bank of Baroda", "Union Bank of India", "Canara Bank");

    public static final List<String> WALLETS = List.of("PhonePe", "Paytm", "Amazon Pay", "MobiKwik");

    public static Optional<Location> location(String name) {
        if (name == null) {
            return Optional.empty();
        }
        return LOCATIONS.stream().filter(l -> l.name().equalsIgnoreCase(name.trim())).findFirst();
    }

    public static Optional<Addon> addon(String code) {
        return ADDONS.stream().filter(a -> a.code().equalsIgnoreCase(code)).findFirst();
    }
}
