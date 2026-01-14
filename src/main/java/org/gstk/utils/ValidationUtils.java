package org.gstk.utils;

import java.net.URI;
import java.net.URISyntaxException;

public class ValidationUtils {
    public static void checkValidZoom(String zoom) throws InvalidZoomException {
        try {
            int zoomInt = Integer.parseInt(zoom);
            if (zoomInt < 0) {
                throw new InvalidZoomException(zoomInt, "Zoom is too low (below 0)");
            }
            if (zoomInt > 30) {
                throw new InvalidZoomException(zoomInt, "Zoom is too high (max is 30)");
            }
        } catch (NumberFormatException e) {
            throw new InvalidZoomException(zoom, e);
        }
    }

    public static void checkValidZoomLevels(String startZoom, String endZoom) throws InvalidZoomException {
        checkValidZoom(startZoom);
        checkValidZoom(endZoom);

        try {
            int startZoomInt = Integer.parseInt(startZoom);
            int endZoomInt = Integer.parseInt(endZoom);

            if (startZoomInt > endZoomInt) {
                throw new InvalidZoomException(
                    startZoomInt, endZoomInt,
                    "Start zoom cannot be larger than end zoom"
                );
            }
        } catch (NumberFormatException e) {
            throw new InvalidZoomException(startZoom, endZoom, e);
        }
    }

    public static boolean isValidLayerName(String layerName) {
        if (layerName == null || layerName.isEmpty()) {
            return false;
        }

        return layerName.matches("^[a-zA-Z0-9_]+$");
    }

    public static boolean isValidTileUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }

        int xCount = countSubstrings(url, "{x}");
        int yCount = countSubstrings(url, "{y}");
        int zCount = countSubstrings(url, "{z}");

        if (xCount != 1 || yCount != 1 || zCount != 1) {
            return false;
        }

        String cleanUrl = url
            .replace("{x}", "0")
            .replace("{y}", "0")
            .replace("{z}", "0");

        try {
            new URI(cleanUrl);
        } catch (URISyntaxException e) {
            return false;
        }

        return true;
    }

    private static int countSubstrings(String str, String sub) {
        if (str == null || sub == null) {
            return 0;
        }

        if (str.isEmpty() || sub.isEmpty()) {
            return 0;
        }

        if (str.length() < sub.length()) {
            return 0;
        }

        if (str.length() == sub.length()) {
            return str.equals(sub) ? 1 : 0;
        }

        if (!str.contains(sub)) {
            return 0;
        }

        int count = 0;
        int index = 0;

        while ((index = str.indexOf(sub, index)) != -1) {
            count++;
            index += sub.length();
        }

        return count;
    }

    public static class InvalidZoomException extends RuntimeException {
        public InvalidZoomException(int startZoom, int endZoom, String message) {
            super(String.format("%s (zoom %d -> %d)", message, startZoom, endZoom));
        }

        public InvalidZoomException(String startZoom, String endZoom, Exception e) {
            super(String.format("%s: %s (zoom %s -> %s)", e.getClass().getName(), e.getMessage(), startZoom, endZoom));
        }

        public InvalidZoomException(int zoom, String message) {
            super(String.format("%s (zoom %d)", message, zoom));
        }

        public InvalidZoomException(String zoom, Exception e) {
            super(String.format("%s: %s (zoom %s)", e.getClass().getName(), e.getMessage(), zoom));
        }
    }
}
