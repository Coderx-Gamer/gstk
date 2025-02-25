package org.gstk.utils;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Polygon;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class TileUtils {
    /**
     * Finds all tiles within a region of latitudes and longitudes at a zoom level.
     * If the `region` polygon is left open (first entry is not equal to the last), the first is appended to the end.
     *
     * @param region List of `org.locationtech.jts.geom.Coordinate` in decimal degree latitude and longitude format.
     * @param zoom Tile zoom level.
     * @return List of `Tile` within the region.
     */
    public static List<Tile> findTilesInRegion(List<Coordinate> region, int zoom) {
        List<Coordinate> regionCopy = new ArrayList<>(region);
        if (!regionCopy.get(0).equals2D(regionCopy.get(regionCopy.size() - 1))) {
            regionCopy.add(regionCopy.get(0));
        }

        List<Coordinate> tileCoordinates = new ArrayList<>();
        for (Coordinate coordinate : regionCopy) {
            Tile tile = latLonToTile(coordinate.x, coordinate.y, zoom);
            tileCoordinates.add(new Coordinate(tile.x, tile.y));
        }

        Polygon regionPolygon = new GeometryFactory().createPolygon(tileCoordinates.toArray(new Coordinate[0]));

        return findTilesInPolygon(regionPolygon, zoom);
    }

    /**
     * Same as `downloadTile`, except if the download fails, `maxTries` of attempts are done.
     *
     * @param tile A `Tile` object.
     * @param url GIS server URL with placeholders.
     * @param maxTries Number of attempts allowed if download fails.
     * @param delayMs Delay between download attempts in milliseconds.
     * @return The image bytes.
     * @throws IOException If `downloadTile` throws an exception.
     */
    public static byte[] downloadTileWithRetries(Tile tile, String url, int maxTries, int delayMs) throws IOException, InterruptedException {
        int tries = 0;
        while (tries < maxTries) {
            try {
                return downloadTile(tile, url);
            } catch (IOException e) {
                if (++tries >= maxTries) {
                    throw e;
                }
                if (delayMs != 0) {
                    Thread.sleep(delayMs);
                }
            }
        }
        throw new IOException("Failed to throw downloadTile exception");
    }

    private static Tile latLonToTile(double lat, double lon, int zoom) {
        lat = Math.max(Math.min(lat, 85.05112878), -85.05112878);

        double x = (lon + 180.0) / 360.0;
        double y = (1.0 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2.0;

        int tiles = 1 << zoom;

        int tileX = (int) Math.floor(x * tiles);
        int tileY = (int) Math.floor(y * tiles);

        return new Tile(tileX, tileY, zoom);
    }

    private static List<Tile> findTilesInPolygon(Polygon polygon, int zoom) {
        List<Tile> tiles = new ArrayList<>();

        Envelope bounds = polygon.getEnvelopeInternal();
        int minX = (int) Math.floor(bounds.getMinX());
        int minY = (int) Math.floor(bounds.getMinY());
        int maxX = (int) Math.ceil(bounds.getMaxX());
        int maxY = (int) Math.ceil(bounds.getMaxY());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                Polygon tilePolygon = createTilePolygon(x, y);
                if (polygon.intersects(tilePolygon)) {
                    tiles.add(new Tile(x, y, zoom));
                }
            }
        }

        return tiles;
    }

    private static Polygon createTilePolygon(int x, int y) {
        Coordinate[] tileCoordinates = new Coordinate[]{
                new Coordinate(x, y),
                new Coordinate(x + 1, y),
                new Coordinate(x + 1, y + 1),
                new Coordinate(x, y + 1),
                new Coordinate(x, y)
        };
        return new GeometryFactory().createPolygon(tileCoordinates);
    }

    private static byte[] downloadTile(Tile tile, String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(getTileUrl(tile, url)).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        connection.connect();

        if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
            throw new IOException("Failed to download tile: HTTP error code: " + connection.getResponseCode());
        }

        try (InputStream in = connection.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
            byte[] data = out.toByteArray();
            connection.disconnect();

            if (!ImageUtils.isPng(data)) {
                byte[] pngData = ImageUtils.convertBytesToPng(data);
                if (pngData != null) {
                    return pngData;
                } else {
                    throw new IOException("Failed to download tile, likely corrupted or invalid image");
                }
            }
            return data;
        }
    }

    private static String getTileUrl(Tile tile, String url) {
        return url.replace("{x}", String.valueOf(tile.x))
                  .replace("{y}", String.valueOf(tile.y))
                  .replace("{z}", String.valueOf(tile.zoom));
    }

    public record Tile(int x, int y, int zoom) {}
}