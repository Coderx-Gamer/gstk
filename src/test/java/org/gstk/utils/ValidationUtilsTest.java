package org.gstk.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ValidationUtilsTest {
    @Test
    void testIsValidXyz() {
        assertTrue(ValidationUtils.isValidXyz("{z}/{x}/{y}"), "Standard coordinates");

        assertFalse(ValidationUtils.isValidXyz("{z}/{x}/{x}"), "Duplicate coordinates");
        assertFalse(ValidationUtils.isValidXyz("{x}/{y}"), "Only two coordinates");
        assertFalse(ValidationUtils.isValidXyz("{z}/{x}/{y}/{y}"), "Too many coordinates");

        assertFalse(ValidationUtils.isValidXyz("invalid"), "Nonsensical coordinates");
    }

    @Test
    void testIsValidZoom() {
        assertTrue(ValidationUtils.isValidZoom("5"), "In-range zoom");

        assertFalse(ValidationUtils.isValidZoom("-1"), "Negative zoom");
        assertFalse(ValidationUtils.isValidZoom("100"), "Zoom too large");

        assertFalse(ValidationUtils.isValidZoom("invalid"), "Nonsensical zoom");
    }

    @Test
    void testIsValidLayerName() {
        assertTrue(ValidationUtils.isValidLayerName("ValidLayer123"), "Valid layer name");
        assertTrue(ValidationUtils.isValidLayerName("Valid_Layer_123"), "Valid layer name with underscores");

        assertFalse(ValidationUtils.isValidLayerName("Invalid Layer"), "Invalid layer name with spaces");
        assertFalse(ValidationUtils.isValidLayerName("InvalidLayer!"), "Invalid layer name with special characters");
    }

    @Test
    void testIsValidTileUrl() {
        assertTrue(ValidationUtils.isValidTileUrl("https://www.examplegis.com/tile/{z}/{x}/{y}"), "Valid GIS URL");
        assertTrue(ValidationUtils.isValidTileUrl("https://www.examplegis.com/tile/{z}/{x}/{y}.png"), "Valid GIS URL with file extension");

        assertFalse(ValidationUtils.isValidTileUrl("https://www.examplegis.com/tile/"), "GIS URL without coordinates");
        assertFalse(ValidationUtils.isValidTileUrl("https://www.examplegis.com/tile/{x}/{x}/{y}"), "GIS URL with duplicate coordinates");
        assertFalse(ValidationUtils.isValidTileUrl("https://www.examplegis.com/tile/{x}/{y}"), "GIS URL with only two coordinates");
        assertFalse(ValidationUtils.isValidTileUrl("https://www.examplegis.com/tile/{z}/{x}/{y}/{y}"), "GIS URL with too many coordinates");
    }

    @Test
    void testIsValidPort() {
        assertTrue(ValidationUtils.isValidPort("8080"), "Valid port");

        assertFalse(ValidationUtils.isValidPort("-7000"), "Negative port");
        assertFalse(ValidationUtils.isValidPort("72000"), "Port out of positive range");

        assertFalse(ValidationUtils.isValidPort("invalid"), "Invalid port number");
    }
}