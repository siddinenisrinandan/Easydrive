package com.driveease.util;

/** Small geometry helpers for the tracking simulator. */
public final class Geo {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private Geo() {
    }

    /** Great-circle distance in km. */
    public static double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 2 * EARTH_RADIUS_KM * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Point on a quadratic Bezier curve p0 -> (control) -> p2 at t in [0,1]. Returns {lat, lng}. */
    public static double[] bezier(double lat0, double lng0, double latC, double lngC,
                                  double lat2, double lng2, double t) {
        double u = 1 - t;
        double lat = u * u * lat0 + 2 * u * t * latC + t * t * lat2;
        double lng = u * u * lng0 + 2 * u * t * lngC + t * t * lng2;
        return new double[]{lat, lng};
    }

    /** Approximate length of the Bezier route in km by sampling. */
    public static double bezierLengthKm(double lat0, double lng0, double latC, double lngC,
                                        double lat2, double lng2) {
        int steps = 80;
        double total = 0;
        double[] prev = bezier(lat0, lng0, latC, lngC, lat2, lng2, 0);
        for (int i = 1; i <= steps; i++) {
            double[] p = bezier(lat0, lng0, latC, lngC, lat2, lng2, (double) i / steps);
            total += haversineKm(prev[0], prev[1], p[0], p[1]);
            prev = p;
        }
        return total;
    }

    public static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
