package com.eaglepoint.console.security;

public class MaskingUtil {
    private static final double GRID_DEGREES = 0.00145;

    public static double maskCoordinate(double coord) {
        return Math.round(coord / GRID_DEGREES) * GRID_DEGREES;
    }

    public static double maskLat(double lat) {
        return maskCoordinate(lat);
    }

    public static double maskLon(double lon) {
        return maskCoordinate(lon);
    }

    public static String maskSensitiveField(String value, boolean unmask) {
        if (unmask) return value;
        return "[MASKED]";
    }
}
