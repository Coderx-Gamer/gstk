package org.gstk.utils;

import org.junit.jupiter.api.Test;

import static org.gstk.utils.ValidationUtils.*;
import static org.junit.jupiter.api.Assertions.*;

class ValidationUtilsTest {
    @Test
    void testCheckValidZoom() {
        assertDoesNotThrow(() -> checkValidZoom("0"), "Zero value zoom");
        assertDoesNotThrow(() -> checkValidZoom("5"), "In-range zoom");
        assertDoesNotThrow(() -> checkValidZoom("25"), "High but in bounds zoom");

        assertThrows(InvalidZoomException.class, () -> checkValidZoom("-1"), "Zoom too low");
        assertThrows(InvalidZoomException.class, () -> checkValidZoom("100"), "Zoom too high");

        assertThrows(InvalidZoomException.class, () -> checkValidZoom("invalid"), "Not an integer");
        assertThrows(InvalidZoomException.class, () -> checkValidZoom("2147483648"), "Too big to be an integer");
    }

    @Test
    void testCheckValidZoomLevels() {
        assertDoesNotThrow(() -> checkValidZoomLevels("0", "0"), "Zero value zoom levels");
        assertDoesNotThrow(() -> checkValidZoomLevels("0", "17"), "Valid example zoom range");

        assertThrows(InvalidZoomException.class, () -> checkValidZoomLevels("12", "9"), "Start bigger than end");
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
}
