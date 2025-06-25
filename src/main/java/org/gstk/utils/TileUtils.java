package org.gstk.utils;

import org.gstk.Constants;
import org.gstk.Download;
import org.gstk.model.Region;
import org.locationtech.jts.geom.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class TileUtils {
    /**
     * Finds all tiles within a `Region` at a zoom level.
     *
     * @param region Object of `org.gstk.model.Region`.
     * @param zoom Tile zoom level.
     * @return Set of `Tile` within the region.
     */
    public static Set<Tile> findTilesInRegion(Region region, int zoom) {
        Set<Tile> tiles = new HashSet<>();

        MultiPolygon polygons = region.polygons();
        Polygon[] polygonArray = new Polygon[polygons.getNumGeometries()];

        for (int i = 0; i < polygons.getNumGeometries(); i++) {
            polygonArray[i] = (Polygon) polygons.getGeometryN(i);
        }

        GeometryFactory gf = new GeometryFactory();
        for (Polygon polygon : polygonArray) {
            Polygon transformedPolygon = transformPolygon(polygon, gf, point -> {
                Tile tile = latLonToTile(point.getY(), point.getX(), zoom);
                return new Coordinate(tile.x(), tile.y());
            });
            tiles.addAll(findTilesInPolygon(transformedPolygon, zoom));
        }

        return tiles;
    }

    /**
     * Multithreaded download of a tile list.
     * Defaults to `downloadTiles` (single-threaded) if threads are set to 1.
     *
     * @param tiles List of `Tile` to download.
     * @param url Tile server URL.
     * @param layer Layer name for the failed download callback (can be null).
     * @param threads Number of threads to use.
     * @param maxTries Maximum number of download attempts (set to -1 for infinite).
     * @param delayMs Delay between download attempts in milliseconds.
     * @param progressCallback `Download.ProgressCallback` object for updating progress (can be null).
     * @param errorCallback `Download.ErrorCallback` object for logging failed downloads (can be null).
     * @return Set of `org.geotools.geopkg.Tile` downloaded.
     */
    public static Set<org.geotools.geopkg.Tile> downloadTilesMultithread(List<Tile> tiles,
                                                                         String url,
                                                                         String layer,
                                                                         int threads,
                                                                         int maxTries,
                                                                         int delayMs,
                                                                         Download.ProgressCallback progressCallback,
                                                                         Download.ErrorCallback errorCallback) {
        if (tiles.isEmpty()) return ConcurrentHashMap.newKeySet();

        if (threads == 1) {
            return downloadTiles(tiles, url, layer, maxTries, delayMs, progressCallback, errorCallback);
        }

        Set<org.geotools.geopkg.Tile> downloadedTiles = ConcurrentHashMap.newKeySet();

        int totalTiles = tiles.size();
        int chunkSize = (int) Math.ceil((double) totalTiles / threads);

        final ExecutorService executor = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, totalTiles);
            if (start >= end) break;

            List<Tile> tileChunk = tiles.subList(start, end);
            executor.execute(() -> downloadedTiles.addAll(downloadTiles(tileChunk, url, layer, maxTries, delayMs, progressCallback, errorCallback)));
        }

        executor.shutdown();

        try {
            if (!executor.awaitTermination(Constants.EXECUTOR_TIMEOUT_HOURS, TimeUnit.HOURS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }

        return downloadedTiles;
    }

    /**
     * Single-threaded download of a tile list.
     *
     * @param tiles List of `Tile` to download.
     * @param url Tile server URL.
     * @param layer Layer name for the failed download callback (can be null).
     * @param maxTries Maximum number of download attempts (set to -1 for infinite).
     * @param delayMs Delay between download attempts in milliseconds.
     * @param progressCallback `Download.ProgressCallback` object for updating progress (can be null).
     * @param errorCallback `Download.ErrorCallback` object for logging failed downloads (can be null).
     * @return Set of `org.geotools.geopkg.Tile` downloaded.
     */
    public static Set<org.geotools.geopkg.Tile> downloadTiles(List<Tile> tiles,
                                                              String url,
                                                              String layer,
                                                              int maxTries,
                                                              int delayMs,
                                                              Download.ProgressCallback progressCallback,
                                                              Download.ErrorCallback errorCallback) {
        if (tiles.isEmpty()) return new HashSet<>();

        Set<org.geotools.geopkg.Tile> downloadedTiles = new HashSet<>();

        for (Tile tile : tiles) {
            try {
                byte[] image = downloadTileWithRetries(tile, url, maxTries, delayMs);
                downloadedTiles.add(new org.geotools.geopkg.Tile(tile.zoom(), tile.x(), tile.y(), image));
                if (progressCallback != null) {
                    progressCallback.step();
                }
            } catch (Exception e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                if (errorCallback != null) {
                    errorCallback.error(new Download.FailedTileDownload(tile, layer, url, e));
                }
            }
        }

        return downloadedTiles;
    }

    /**
     * Download a tile from a tile server with retries.
     *
     * @param tile Tile to download.
     * @param url Tile server URL.
     * @param maxTries Maximum number of download attempts (set to -1 for infinite).
     * @param delayMs Delay between download attempts in milliseconds.
     * @return Byte array of downloaded image data.
     * @throws IOException If `downloadTile` fails over `maxTries` attempts.
     * @throws InterruptedException If `Thread.sleep` is interrupted.
     */
    public static byte[] downloadTileWithRetries(Tile tile, String url, int maxTries, int delayMs) throws IOException, InterruptedException {
        int tries = 0;
        while (tries < maxTries || maxTries == -1) {
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
        throw new IOException();
    }

    private static Polygon transformPolygon(Polygon polygon, GeometryFactory gf, Function<Coordinate, Coordinate> transformer) {
        LinearRing shell = transformRing(polygon.getExteriorRing(), gf, transformer);

        LinearRing[] holes = new LinearRing[polygon.getNumInteriorRing()];
        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
            holes[i] = transformRing(polygon.getInteriorRingN(i), gf, transformer);
        }

        return gf.createPolygon(shell, holes);
    }

    private static LinearRing transformRing(LinearRing ring, GeometryFactory gf, Function<Coordinate, Coordinate> transformer) {
        Coordinate[] coordinates = ring.getCoordinates();
        Coordinate[] transformed = new Coordinate[coordinates.length];
        for (int i = 0; i < coordinates.length; i++) {
            transformed[i] = transformer.apply(coordinates[i]);
        }
        return gf.createLinearRing(transformed);
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

    private static Set<Tile> findTilesInPolygon(Polygon polygon, int zoom) {
        Set<Tile> tiles = new HashSet<>();

        Envelope bounds = polygon.getEnvelopeInternal();
        int minX = (int) Math.floor(bounds.getMinX());
        int minY = (int) Math.floor(bounds.getMinY());
        int maxX = (int) Math.ceil(bounds.getMaxX());
        int maxY = (int) Math.ceil(bounds.getMaxY());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                Polygon tilePolygon = createTilePolygon(x, y);
                Tile tile = new Tile(x, y, zoom);
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

            return data;
        }
    }

    private static String getTileUrl(Tile tile, String url) {
        return url.replace("{x}", String.valueOf(tile.x()))
                  .replace("{y}", String.valueOf(tile.y()))
                  .replace("{z}", String.valueOf(tile.zoom()));
    }

    public record Tile(int x, int y, int zoom) {
        @Override
        public boolean equals(Object obj) {
            if (obj instanceof Tile t) {
                return t.x() == x && t.y() == y && t.zoom() == zoom;
            } else {
                return false;
            }
        }
    }
}