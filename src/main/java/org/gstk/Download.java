package org.gstk;

import me.tongfei.progressbar.ProgressBar;
import org.gstk.utils.SQLUtils;
import org.gstk.utils.TileUtils;
import org.locationtech.jts.geom.Coordinate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Download {
    private static final Logger logger = LoggerFactory.getLogger("GSTK");

    public static void startDownloading(String region,
                                        String layer,
                                        String url,
                                        boolean override,
                                        int fixedThreads,
                                        int startZoom,
                                        int endZoom,
                                        String filename) {
        List<Coordinate> regionPolygon = new ArrayList<>();
        for (String coordinate : region.split(";")) {
            String[] latLon = coordinate.split(",");
            try {
                double lat = Double.parseDouble(latLon[0]);
                double lon = Double.parseDouble(latLon[1]);
                regionPolygon.add(new Coordinate(lat, lon));
            } catch (NumberFormatException e) {
                logger.error("Invalid coordinates: {}", coordinate);
                System.exit(1);
            }
        }

        String jdbcUrl = "jdbc:sqlite:" + filename;
        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    CREATE TABLE IF NOT EXISTS tiles (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        layer TEXT NOT NULL,
                        zoom INTEGER NOT NULL,
                        tile_x INTEGER NOT NULL,
                        tile_y INTEGER NOT NULL,
                        png_data BLOB NOT NULL,
                        UNIQUE(layer, zoom, tile_x, tile_y)
                    )
                    """
            )) {
                ps.executeUpdate();
            }

            AtomicInteger totalDownloaded = new AtomicInteger(0);
            AtomicInteger totalFailed = new AtomicInteger(0);

            for (int zoom = startZoom; zoom <= endZoom; zoom++) {
                List<TileUtils.Tile> tilesInRegion = TileUtils.findTilesInRegion(regionPolygon, zoom);
                int threadCount = (fixedThreads < 1) ? Runtime.getRuntime().availableProcessors() : fixedThreads;

                ExecutorService executor = Executors.newFixedThreadPool(threadCount);
                AtomicInteger index = new AtomicInteger(0);

                try (ProgressBar pb = new ProgressBar("Zoom " + zoom, tilesInRegion.size())) {
                    for (int i = 0; i < threadCount; i++) {
                        executor.submit(() -> {
                            try (Connection threadConn = DriverManager.getConnection(jdbcUrl)) {
                                while (true) {
                                    int tileIndex = index.getAndIncrement();
                                    if (tileIndex >= tilesInRegion.size()) break;

                                    TileUtils.Tile tile = tilesInRegion.get(tileIndex);
                                    try {
                                        if (override || !SQLUtils.checkTileExists(tile, layer, threadConn)) {
                                            byte[] pngData = TileUtils.downloadTileWithRetries(
                                                    tile, url, Constants.TILE_DOWNLOAD_ATTEMPTS, Constants.DOWNLOAD_RETRY_DELAY_MS);
                                            SQLUtils.storeTile(tile, layer, pngData, threadConn);
                                            totalDownloaded.incrementAndGet();
                                        }
                                        synchronized (pb) {
                                            pb.stepTo(tileIndex + 1);
                                        }
                                    } catch (IOException e) {
                                        logger.warn("Failed to download tile (z: {}, x: {}, y: {})", tile.zoom(), tile.x(), tile.y());
                                        totalFailed.incrementAndGet();
                                    } catch (InterruptedException e) {
                                        logger.warn("Thread interrupted while downloading tile (z: {}, x: {}, y: {})", tile.zoom(), tile.x(), tile.y());
                                        totalFailed.incrementAndGet();
                                        Thread.currentThread().interrupt();
                                    } catch (SQLException e) {
                                        logger.warn("Failed to store tile (z: {}, x: {}, y: {})", tile.zoom(), tile.x(), tile.y());
                                        totalFailed.incrementAndGet();
                                    }
                                }
                            } catch (SQLException e) {
                                logger.error("Database connection error in thread: {}", e.getMessage());
                            }
                        });
                    }

                    executor.shutdown();
                    if (!executor.awaitTermination(Constants.CHUNK_DOWNLOAD_TIMEOUT_HOURS, TimeUnit.HOURS)) {
                        logger.warn("Some tasks did not finish in the allowed time ({} hours)", Constants.CHUNK_DOWNLOAD_TIMEOUT_HOURS);
                    }
                    pb.stepTo(tilesInRegion.size());
                }
            }

            System.out.println();
            logger.info("Finished downloading region");
            logger.info("Tiles downloaded: {}", totalDownloaded.get());
            logger.info("Tiles failed to download: {}", totalFailed.get());
        } catch (SQLException e) {
            logger.error("Database error: {}", e.getMessage());
            System.exit(1);
        } catch (InterruptedException e) {
            logger.error("Thread interrupted while waiting for download completion");
            Thread.currentThread().interrupt();
        }
    }
}