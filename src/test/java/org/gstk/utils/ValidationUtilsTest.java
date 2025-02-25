package org.gstk.utils;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ValidationUtilsTest {
    @Test
    void testIsValidRegion() {
        // Triangle around Los Angeles, CA
        assertTrue(ValidationUtils.isValidRegion("34.096501,-118.314534;34.049519,-118.185870;34.008607,-118.286687"), "Triangle polygon");

        // Not enough coordinates for polygons
        assertFalse(ValidationUtils.isValidRegion("34.096501,-118.314534;34.049519,-118.185870"), "Two coordinates");
        assertFalse(ValidationUtils.isValidRegion("34.096501,-118.314534"), "One coordinate");

        assertFalse(ValidationUtils.isValidRegion("350.123,-200.123;-100.456,190.456;-91.789,-181.789"), "Out of range coordinates");

        assertFalse(ValidationUtils.isValidRegion("invalid,coordinate;invalid,coordinate;invalid,coordinate"), "Invalid coordinate numbers");
    }

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
        assertFalse(ValidationUtils.isValidLayerName("All"), "Reserved layer name");
    }

    @Test
    void testIsValidGisUrl() {
        assertTrue(ValidationUtils.isValidGisUrl("https://www.examplegis.com/tile/{z}/{x}/{y}"), "Valid GIS URL");

        assertFalse(ValidationUtils.isValidGisUrl("https://www.examplegis.com/tile/"), "GIS URL without coordinates");
        assertFalse(ValidationUtils.isValidGisUrl("https://www.examplegis.com/tile/{x}/{x}/{y}"), "GIS URL with duplicate coordinates");
        assertFalse(ValidationUtils.isValidGisUrl("https://www.examplegis.com/tile/{x}/{y}"), "GIS URL with only two coordinates");
        assertFalse(ValidationUtils.isValidGisUrl("https://www.examplegis.com/tile/{z}/{x}/{y}/{y}"), "GIS URL with too many coordinates");
    }

    @Test
    void testIsValidPort() {
        assertTrue(ValidationUtils.isValidPort("8080"), "Valid port");

        assertFalse(ValidationUtils.isValidPort("-7000"), "Negative port");
        assertFalse(ValidationUtils.isValidPort("72000"), "Port out of positive range");

        assertFalse(ValidationUtils.isValidPort("invalid"), "Invalid port number");
    }
}