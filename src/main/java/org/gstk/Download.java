package org.gstk;

import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import org.geotools.api.referencing.FactoryException;
import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.geopkg.GeoPackage;
import org.geotools.geopkg.Tile;
import org.geotools.geopkg.TileEntry;
import org.geotools.geopkg.TileMatrix;
import org.geotools.referencing.CRS;
import org.gstk.model.Region;
import org.gstk.utils.SQLUtils;
import org.gstk.utils.TileUtils;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.gstk.Main.logger;

public class Download {
    public static void startDownloading(Region region,
                                        String layer,
                                        String url,
                                        boolean override,
                                        int fixedThreads,
                                        int startZoom,
                                        int endZoom,
                                        String geopackagePath) {
        String jdbcUrl = "jdbc:sqlite:" + geopackagePath;
        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(
                        """
                        CREATE TABLE IF NOT EXISTS _gstk_failed_tile_downloads (
                            id INTEGER PRIMARY KEY,
                            layer TEXT NOT NULL,
                            url TEXT,
                            zoom_level INTEGER NOT NULL,
                            tile_column INTEGER NOT NULL,
                            tile_row INTEGER NOT NULL,
                            UNIQUE (layer, url, zoom_level, tile_column, tile_row) ON CONFLICT REPLACE
                        )
                        """);
            }

            try (GeoPackage gpkg = new GeoPackage(new File(geopackagePath))) {
                gpkg.init();

                TileEntry entry = initGeopackage(gpkg, layer);
                createTileMatrices(layer, entry, startZoom, endZoom, conn);

                int totalTiles = 0;
                int totalSaved = 0;

                int threads = fixedThreads < 1 ? Runtime.getRuntime().availableProcessors() : fixedThreads;

                List<FailedTileDownload> fails = Collections.synchronizedList(new ArrayList<>());

                for (int z = startZoom; z <= endZoom; z++) {
                    List<TileUtils.Tile> tilesInRegion = new ArrayList<>(TileUtils.findTilesInRegion(region, z));

                    if (!override) {
                        List<TileUtils.Tile> tilesInRegionCopy = new ArrayList<>(tilesInRegion);
                        for (TileUtils.Tile t : tilesInRegionCopy) {
                            if (SQLUtils.doesTileExist(t, layer, conn)) {
                                tilesInRegion.remove(t);
                            }
                        }
                    }

                    totalTiles += tilesInRegion.size();

                    try (ProgressBar pb = new ProgressBarBuilder()
                            .setInitialMax(tilesInRegion.size())
                            .setTaskName("Zoom " + z)
                            .setStyle(ProgressBarStyle.ASCII)
                            .build()
                    ) {
                        for (int i = 0;
                             i < (int) Math.ceil((double) tilesInRegion.size() / Constants.TILE_WRITE_INTERVAL);
                             i++) {
                            int start = i * Constants.TILE_WRITE_INTERVAL;
                            int end = Math.min(start + Constants.TILE_WRITE_INTERVAL, tilesInRegion.size());
                            if (start >= end) break;

                            List<TileUtils.Tile> subset = tilesInRegion.subList(start, end);
                            Set<Tile> tilesToSave = TileUtils.downloadTilesMultithread(
                                    subset,
                                    url,
                                    layer,
                                    subset.size() >= Constants.MAX_SINGLE_THREAD_DOWNLOAD ? threads : 1,
                                    Constants.TILE_DOWNLOAD_ATTEMPTS,
                                    Constants.DOWNLOAD_RETRY_DELAY_MS,
                                    () -> {
                                        synchronized (pb) {
                                            pb.step();
                                        }
                                    },
                                    fails::add
                            );

                            totalSaved += tilesToSave.size();
                            for (Tile t : tilesToSave) {
                                SQLUtils.updateTile(t, layer, conn);
                            }
                        }
                    }
                }

                System.out.println();
                for (FailedTileDownload fail : fails) {
                    logFailedTileDownload(fail);
                    SQLUtils.addFailedTileDownload(fail, conn);
                }
                if (!fails.isEmpty()) System.out.println();

                logger.info("Finished saving {}/{} tiles", totalSaved, totalTiles);
                logger.info("Failed downloads: {}", fails.size());
            } catch (IOException e) {
                logger.error(e.getMessage());
            } catch (FactoryException e) {
                logger.error("Failed to retrieve EPSG:3857 from authority: {}", e.getMessage());
            }
        } catch (SQLException e) {
            logger.error("SQL error: {}", e.getMessage());
        }
    }

    public static TileEntry initGeopackage(GeoPackage gpkg, String layer) throws SQLException, IOException, FactoryException {
        TileEntry entry;
        if (gpkg.tile(layer) == null) {
            entry = new TileEntry();
            entry.setTableName(layer);

            double max = 20037508.34;
            entry.setBounds(new ReferencedEnvelope(-max, max, -max, max, CRS.decode("EPSG:3857")));

            gpkg.create(entry);
        } else {
            entry = gpkg.tile(layer);
        }
        return entry;
    }

    public static void createTileMatrices(String layer, TileEntry entry, int startZoom, int endZoom, Connection conn) throws SQLException {
        double initialResolution = 156543.03392804097;
        for (int z = startZoom; z <= endZoom; z++) {
            int matrixBound = (int) Math.pow(2, z);
            double pixelSize = initialResolution / Math.pow(2, z);

            entry.getTileMatricies().add(new TileMatrix(
                    z, matrixBound, matrixBound, 256, 256, pixelSize, pixelSize));
        }

        SQLUtils.clearTileMatrices(layer, conn);
        SQLUtils.addTileMatrices(entry.getTileMatricies(), layer, conn);
        SQLUtils.addTileMatrixSet(entry.getBounds(), 3857, layer, conn);
    }

    public static void logFailedTileDownload(FailedTileDownload fail) {
        if (fail.exception() != null) {
            logger.error("Failed to download tile (z: {}, x: {}, y: {}): {}",
                    fail.tile().zoom(), fail.tile().x(), fail.tile().y(),
                    fail.exception().getMessage());
        } else {
            logger.error("Failed to download tile (z: {}, x: {}, y: {})",
                    fail.tile().zoom(), fail.tile().x(), fail.tile().y());
        }
    }

    public interface ProgressCallback {
        void step();
    }

    public interface ErrorCallback {
        void error(FailedTileDownload fail);
    }

    public record FailedTileDownload(TileUtils.Tile tile, String layer, String url, Exception exception) {}
}