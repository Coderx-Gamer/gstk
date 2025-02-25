package org.gstk.utils;

import java.net.URI;
import java.net.URISyntaxException;

public class ValidationUtils {
    public static boolean isValidRegion(String region) {
        if (region == null || region.isEmpty()) {
            return false;
        }

        if (!region.contains(";")) {
            return false;
        }

        String[] parts = region.split(";");

        if (parts.length < 3) {
            return false;
        }

        for (String part : parts) {
            if (!isValidCoordinate(part)) {
                return false;
            }
        }

        return true;
    }

    public static boolean isValidXyz(String xyz) {
        if (xyz == null || xyz.isEmpty()) {
            return false;
        }

        if (!xyz.contains("/")) {
            return false;
        }

        String[] parts = xyz.split("/");

        if (parts.length != 3) {
            return false;
        }

        boolean xFound = false;
        boolean yFound = false;
        boolean zFound = false;
        for (String part : parts) {
            switch (part) {
                case "{x}":
                    if (xFound) {
                        return false;
                    }
                    xFound = true;
                    break;
                case "{y}":
                    if (yFound) {
                        return false;
                    }
                    yFound = true;
                    break;
                case "{z}":
                    if (zFound) {
                        return false;
                    }
                    zFound = true;
                    break;
            }
        }

        return xFound && yFound && zFound;
    }

    public static boolean isValidZoom(String zoom) {
        int zoomInt;
        try {
            zoomInt = Integer.parseInt(zoom);
        } catch (NumberFormatException e) {
            return false;
        }

        return zoomInt >= 0 && zoomInt <= 30;
    }

    public static boolean isValidLayerName(String layerName) {
        if (layerName == null || layerName.isEmpty()) {
            return false;
        }

        return layerName.matches("^[a-zA-Z0-9_]+$") && !layerName.equals("All");
    }

    public static boolean isValidGisUrl(String url) {
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

    public static boolean isValidPort(String port) {
        int portInt;
        try {
            portInt = Integer.parseInt(port);
        } catch (NumberFormatException e) {
            return false;
        }

        return portInt >= 0 && portInt <= 65535;
    }

    private static boolean isValidCoordinate(String coordinate) {
        if (coordinate.isEmpty()) {
            return false;
        }

        if (!coordinate.contains(",")) {
            return false;
        }

        String[] parts = coordinate.split(",");

        if (parts.length != 2) {
            return false;
        }

        double lat, lon;
        try {
            lat = Double.parseDouble(parts[0]);
            lon = Double.parseDouble(parts[1]);
        } catch (NumberFormatException e) {
            return false;
        }

        return lat > -90.0 && lat < 90.0 && lon > -180.0 && lon < 180.0;
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
}