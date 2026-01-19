package org.gstk;

import jakarta.xml.bind.JAXBException;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import org.gstk.db.TileDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.gstk.utils.TileUtils.*;

public class Downloader {
    private static final Logger LOGGER = LoggerFactory.getLogger(Downloader.class);

    public static AtomicBoolean killFlag = new AtomicBoolean(false);

    private final TileDB db;
    private final Region region;
    private final String tileUrl;
    private final int threadCount;
    public FailedTiles fails;

    public AtomicInteger downloadedTileCount = new AtomicInteger(0);
    public AtomicInteger failedTileCount = new AtomicInteger(0);

    public Downloader(TileDB db, Region region, String tileUrl, int threadCount, File failedDownloadsFile) {
        this.db = db;
        this.region = region;
        this.tileUrl = tileUrl;
        this.threadCount = threadCount;

        fails = null;
        if (failedDownloadsFile != null) {
            try {
                fails = new FailedTiles(failedDownloadsFile, db.getIdentifier());
            } catch (JAXBException e) {
                LOGGER.error(
                    "Failed to read {} for failed tile downloads",
                    failedDownloadsFile.getName(),
                    e
                );
            }
        }
    }

    public void start(int startZoom, int endZoom, boolean override) {
        for (int zoom = startZoom; zoom <= endZoom; zoom++) {
            List<TilePosition> tiles = new ArrayList<>(findTilesInRegion(region, zoom));
            if (!override) {
                tiles.removeIf(db::doesTileExist);
            }
            if (tiles.isEmpty()) {
                LOGGER.info("Skipping zoom level {}, no tiles need to be downloaded", zoom);
                continue;
            }
            List<List<TilePosition>> tileChunks = tilesToChunks(tiles);

            BlockingQueue<TileData> tilesToWrite = new LinkedBlockingQueue<>(tiles.size());
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            final int currentZoom = zoom;
            Thread consumer = new Thread(() -> {
                try {
                    int totalTiles = tiles.size();
                    try (ProgressBar pb = new ProgressBarBuilder()
                        .setTaskName("Zoom " + currentZoom)
                        .setInitialMax(totalTiles)
                        .setStyle(ProgressBarStyle.ASCII)
                        .setMaxRenderedLength(120)
                        .build())
                    {
                        while (!executor.isTerminated() || !tilesToWrite.isEmpty()) {
                            if (killFlag.get()) return;
                            TileData tile = tilesToWrite.poll(100, TimeUnit.MILLISECONDS);
                            if (tile != null) {
                                try {
                                    db.storeTile(tile);
                                    pb.step();
                                    downloadedTileCount.incrementAndGet();
                                } catch (Exception e) {
                                    logFailedTile(tile.pos(), FailedTiles.FailType.WRITE, e);
                                }
                            }
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "Tile-Writer");
            consumer.start();

            for (List<TilePosition> c : tileChunks) {
                final List<TilePosition> chunk = new ArrayList<>(c);
                executor.submit(() -> {
                    try {
                        for (TilePosition pos : chunk) {
                            if (killFlag.get()) return;
                            try {
                                TileData tile = downloadTileWithRetries(
                                    pos,
                                    tileUrl,
                                    Constants.TILE_DOWNLOAD_ATTEMPTS,
                                    Constants.DOWNLOAD_RETRY_DELAY_MS
                                );
                                tilesToWrite.put(tile);
                            } catch (IOException e) {
                                logFailedTile(pos, FailedTiles.FailType.DOWNLOAD, e);
                            }
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }

            try {
                executor.shutdown();
                if (!executor.awaitTermination(31, TimeUnit.DAYS)) {
                    throw new IllegalStateException("Downloader threads did not terminate within 31 days");
                }
                consumer.join();
                if (killFlag.get()) {
                    db.close();
                    System.exit(0);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public void repair() {
        if (fails == null) {
            LOGGER.info("No fails file specified, canceling repair");
            return;
        }

        int failedTiles = fails.fails.fails.size();
        int fixedTiles = 0;

        try (ProgressBar pb = new ProgressBarBuilder()
            .setTaskName("Re-downloading tiles")
            .setInitialMax(failedTiles)
            .setStyle(ProgressBarStyle.ASCII)
            .setMaxRenderedLength(120)
            .build())
        {
            for (FailedTiles.Fail fail : fails.fails.fails) {
                if (killFlag.get()) return;
                TilePosition pos = new TilePosition(fail.x, fail.y, fail.zoom);
                try {
                    TileData tile = downloadTileWithRetries(
                        pos,
                        fail.url,
                        Constants.TILE_DOWNLOAD_ATTEMPTS,
                        Constants.DOWNLOAD_RETRY_DELAY_MS
                    );
                    db.storeTile(tile);
                    pb.step();
                    fixedTiles++;
                } catch (IOException e) {
                    LOGGER.error("Failed to re-download tile {}", pos, e);
                } catch (SQLException e) {
                    LOGGER.error("Failed to write tile {} to database", pos, e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOGGER.error("Unknown tile error", e);
                }
            }
        }

        if (killFlag.get()) {
            db.close();
            System.exit(0);
        }

        if (fixedTiles < failedTiles) {
            LOGGER.warn("{} tiles failed to re-download", failedTiles - fixedTiles);
        }
        LOGGER.info("Finished re-downloading {}/{} failed tile entries", fixedTiles, failedTiles);
        LOGGER.info("You can remove the failed tile entries by deleting {}", fails.file.getName());
    }

    private synchronized void logFailedTile(TilePosition pos, FailedTiles.FailType type, Exception e) {
        LOGGER.error("Failed to {} tile {}", type.name, pos, e);
        failedTileCount.incrementAndGet();

        if (fails != null) {
            fails.addFailedTile(pos.zoom(), pos.x(), pos.y(), type, tileUrl);
            try {
                fails.write();
            } catch (JAXBException ex) {
                LOGGER.error("Failed to write to {} for failed tile downloads", fails.file.getName(), ex);
            }
        }
    }

    private List<List<TilePosition>> tilesToChunks(List<TilePosition> tiles) {
        List<List<TilePosition>> tileChunks = new ArrayList<>();
        if (threadCount > 1) {
            int size = tiles.size();
            int chunkSize = size / threadCount;
            int remainder = size % threadCount;

            int start = 0;
            for (int i = 0; i < threadCount; i++) {
                int end = start + chunkSize + (i < remainder ? 1 : 0);
                if (start >= size) {
                    tileChunks.add(new ArrayList<>());
                } else {
                    tileChunks.add(new ArrayList<>(tiles.subList(start, end)));
                }
                start = end;
            }
        } else {
            tileChunks.add(new ArrayList<>(tiles));
        }
        return tileChunks;
    }
}
