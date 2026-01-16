package org.gstk.utils;

import org.gstk.Region;
import org.locationtech.jts.geom.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public class TileUtils {
    private static final Logger LOGGER = LoggerFactory.getLogger(TileUtils.class);

    public static Set<TilePosition> findTilesInRegion(Region region, int zoom) {
        Set<TilePosition> tiles = new HashSet<>();

        MultiPolygon polygons = region.polygons();
        Polygon[] polygonArray = new Polygon[polygons.getNumGeometries()];

        for (int i = 0; i < polygons.getNumGeometries(); i++) {
            polygonArray[i] = (Polygon) polygons.getGeometryN(i);
        }

        GeometryFactory gf = new GeometryFactory();
        for (Polygon polygon : polygonArray) {
            Polygon transformedPolygon = transformPolygon(polygon, gf, point -> {
                TilePosition tile = latLonToTile(point.getY(), point.getX(), zoom);
                return new Coordinate(tile.x(), tile.y());
            });
            tiles.addAll(findTilesInPolygon(transformedPolygon, zoom));
        }

        return tiles;
    }

    public static TileData downloadTileWithRetries(TilePosition tile, String url, int maxTries, int delayMs)
        throws IOException, InterruptedException
    {
        int tries = 0;
        while (tries < maxTries) {
            try {
                return downloadTile(tile, url);
            } catch (IOException e) {
                if (++tries >= maxTries) {
                    throw e;
                }
                if (delayMs != 0) {
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw ex;
                    }
                }
            }
        }
        throw new IOException();
    }

    private static Polygon transformPolygon(
        Polygon polygon,
        GeometryFactory gf,
        Function<Coordinate, Coordinate> transformer)
    {
        LinearRing shell = transformRing(polygon.getExteriorRing(), gf, transformer);

        LinearRing[] holes = new LinearRing[polygon.getNumInteriorRing()];
        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
            holes[i] = transformRing(polygon.getInteriorRingN(i), gf, transformer);
        }

        return gf.createPolygon(shell, holes);
    }

    private static LinearRing transformRing(
        LinearRing ring,
        GeometryFactory gf,
        Function<Coordinate, Coordinate> transformer)
    {
        Coordinate[] coordinates = ring.getCoordinates();
        Coordinate[] transformed = new Coordinate[coordinates.length];
        for (int i = 0; i < coordinates.length; i++) {
            transformed[i] = transformer.apply(coordinates[i]);
        }
        return gf.createLinearRing(transformed);
    }

    private static TilePosition latLonToTile(double lat, double lon, int zoom) {
        lat = Math.max(Math.min(lat, 85.05112878), -85.05112878);

        double x = (lon + 180.0) / 360.0;
        double y = (1.0 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2.0;

        int tiles = 1 << zoom;

        int tileX = (int) Math.floor(x * tiles);
        int tileY = (int) Math.floor(y * tiles);

        return new TilePosition(tileX, tileY, zoom);
    }

    private static Set<TilePosition> findTilesInPolygon(Polygon polygon, int zoom) {
        Set<TilePosition> tiles = new HashSet<>();

        Envelope bounds = polygon.getEnvelopeInternal();
        int minX = (int) Math.floor(bounds.getMinX());
        int minY = (int) Math.floor(bounds.getMinY());
        int maxX = (int) Math.ceil(bounds.getMaxX());
        int maxY = (int) Math.ceil(bounds.getMaxY());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                Polygon tilePolygon = createTilePolygon(x, y);
                TilePosition tile = new TilePosition(x, y, zoom);
                if (polygon.intersects(tilePolygon)) {
                    tiles.add(tile);
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
            new Coordinate(x, y),
        };
        return new GeometryFactory().createPolygon(tileCoordinates);
    }

    private static TileData downloadTile(TilePosition pos, String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(getTileUrl(pos, url)).openConnection();
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
                    data = pngData;
                } else {
                    LOGGER.warn("Unable to convert non-png tile at {} to png", pos);
                }
            }

            return new TileData(pos, data);
        }
    }

    private static String getTileUrl(TilePosition tile, String url) {
        return url.replace("{x}", String.valueOf(tile.x()))
                  .replace("{y}", String.valueOf(tile.y()))
                  .replace("{z}", String.valueOf(tile.zoom()));
    }

    public record TilePosition(int x, int y, int zoom) {
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof TilePosition t) {
                return t.x() == x && t.y() == y && t.zoom() == zoom;
            } else {
                return false;
            }
        }

        @Override
        public String toString() {
            return String.format("[Zoom: %d, Column: %d, Row: %d]", zoom, x, y);
        }
    }

    public record TileData(TilePosition pos, byte[] data) {}
}
