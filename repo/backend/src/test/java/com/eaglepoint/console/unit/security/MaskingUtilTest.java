package com.eaglepoint.console.unit.security;

import com.eaglepoint.console.security.MaskingUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MaskingUtilTest {

    @Test
    void maskCoordinateSnapsToGrid() {
        // 0.00145 degree grid
        double lat = 37.7749;
        double masked = MaskingUtil.maskCoordinate(lat);
        // Should be rounded to nearest 0.00145
        double expected = Math.round(lat / 0.00145) * 0.00145;
        assertEquals(expected, masked, 1e-9);
    }

    @Test
    void maskCoordinateZero() {
        assertEquals(0.0, MaskingUtil.maskCoordinate(0.0), 1e-9);
    }

    @Test
    void maskCoordinateNegative() {
        double lat = -33.8688;
        double masked = MaskingUtil.maskCoordinate(lat);
        double expected = Math.round(lat / 0.00145) * 0.00145;
        assertEquals(expected, masked, 1e-9);
    }

    @Test
    void maskCoordinateMaxLatitude() {
        double lat = 90.0;
        double masked = MaskingUtil.maskCoordinate(lat);
        assertNotNull(masked);
    }

    @Test
    void maskSensitiveFieldReturnsMaskedWhenNotUnmasked() {
        String result = MaskingUtil.maskSensitiveField("real-value", false);
        assertEquals("[MASKED]", result);
    }

    @Test
    void maskSensitiveFieldReturnsValueWhenUnmasked() {
        String result = MaskingUtil.maskSensitiveField("real-value", true);
        assertEquals("real-value", result);
    }

    @Test
    void maskSensitiveFieldNullInput() {
        String result = MaskingUtil.maskSensitiveField(null, false);
        assertEquals("[MASKED]", result);
    }

    @Test
    void maskSensitiveFieldNullUnmasked() {
        String result = MaskingUtil.maskSensitiveField(null, true);
        assertNull(result);
    }
}
