package org.gstk;

import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarBuilder;
import me.tongfei.progressbar.ProgressBarStyle;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.geotools.api.referencing.FactoryException;
import org.geotools.geopkg.GeoPackage;
import org.geotools.geopkg.Tile;
import org.geotools.geopkg.TileEntry;
import org.gstk.model.Region;
import org.gstk.utils.SQLUtils;
import org.gstk.utils.TileUtils;
import org.gstk.utils.ValidationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static final Logger logger = LoggerFactory.getLogger("GSTK");

    public static void main(String[] args) {
        Options options = createOptions();

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);

            if (cmd.hasOption("h")) {
                printHelp(options);
                System.exit(0);
            }

            if (cmd.hasOption("V")) {
                printVersion();
                System.exit(0);
            }

            String geopackagePath = null;

            if (!cmd.hasOption("tile-count")) {
                List<String> indexedArgs = cmd.getArgList();
                if (indexedArgs.size() != 1 || indexedArgs.get(0).isEmpty()) {
                    logErrorAndExit("Missing or invalid geopackage file (index 0 parameter)", true);
                } else {
                    geopackagePath = indexedArgs.get(0);
                }
            }

            if (!cmd.hasOption("d") && !cmd.hasOption("tile-count")) {
                try {
                    assert geopackagePath != null;
                    File file = new File(geopackagePath);
                    if (!file.exists() || file.isDirectory()) throw new Exception();
                } catch (Exception e) {
                    logErrorAndExit("Invalid geopackage file: {}", false, geopackagePath);
                }
            }

            if (cmd.hasOption("d")) {
                processDownload(cmd, geopackagePath);
            } else if (cmd.hasOption("f")) {
                processFix(cmd, geopackagePath);
            } else if (cmd.hasOption("clear-errors")) {
                processClearErrors(geopackagePath);
            } else if (cmd.hasOption("tile-count")) {
                processTileCount(cmd);
            } else {
                logErrorAndExit("No option specified ( (-d, --download), (-f, --fix), (--tile-count) )", true);
            }
        } catch (Exception e) {
            logErrorAndExit("{}", false, e.getMessage());
        }
    }

    private static void printHelp(Options options) {
        System.out.printf(
                """
                Usage: java -jar ... [options...] <geopackage>
                
                Options:
                  -h, --help          %s
                  -V, --version       %s
                
                Mutually exclusive:
                  -d, --download      %s
                  -f, --fix           %s
                  --clear-errors      %s
                  --tile-count        %s
                
                Download (-d, --download) options:
                  -l, --layer         %s
                  -r, --region        %s
                  -u, --url           %s
                  -o, --override      %s
                  -t, --threads       %s
                
                  -s, --start-zoom    %s
                  -e, --end-zoom      %s
                
                Fix (-f, --fix) options:
                  -u, --url           %s (optional)
                
                Tile count (-t, --tile-count) options:
                  No file parameter required
                
                  -r, --region        %s
                
                  -s, --start-zoom    %s
                  -e, --end-zoom      %s
                """,
                options.getOption("h").getDescription(),
                options.getOption("V").getDescription(),
                options.getOption("d").getDescription(),
                options.getOption("f").getDescription(),
                options.getOption("clear-errors").getDescription(),
                options.getOption("tile-count").getDescription(),
                options.getOption("l").getDescription(),
                options.getOption("r").getDescription(),
                options.getOption("u").getDescription(),
                options.getOption("o").getDescription(),
                options.getOption("t").getDescription(),
                options.getOption("s").getDescription(),
                options.getOption("e").getDescription(),
                options.getOption("u").getDescription(),
                options.getOption("r").getDescription(),
                options.getOption("s").getDescription(),
                options.getOption("e").getDescription()
        );
    }

    private static void printVersion() {
        System.out.println("GSTK " + Constants.PROJECT_VERSION);
    }

    private static void processDownload(CommandLine cmd, String geopackagePath) {
        if (!cmd.hasOption("l") ||
            !cmd.hasOption("r") ||
            !cmd.hasOption("u")
        ) {
            logErrorAndExit("Missing required download options", true);
        }

        if (!cmd.hasOption("s") || !cmd.hasOption("e")) {
            logErrorAndExit("Missing required zoom level options (-s, --start-zoom and -e, --end-zoom)", true);
        }

        String layer;
        String url;
        boolean override = cmd.hasOption("o");
        int threads = -1;
        int startZoom;
        int endZoom;

        String regionString = cmd.getOptionValue("r");
        Region region;

        try {
            region = Region.fromString(regionString);
        } catch (Exception e) {
            logErrorAndExit("Invalid region: {}", false, e.getMessage());
            return;
        }

        if (!ValidationUtils.isValidLayerName(layer = cmd.getOptionValue("l"))) {
            logErrorAndExit("Invalid layer name", true);
        }

        if (!ValidationUtils.isValidTileUrl(url = cmd.getOptionValue("u"))) {
            logErrorAndExit("Invalid tile URL", true);
        }

        if (cmd.hasOption("t")) {
            try {
                threads = Integer.parseInt(cmd.getOptionValue("t"));
                if (threads < 1) {
                    throw new NumberFormatException();
                }
                if (threads > Runtime.getRuntime().availableProcessors()) {
                    logger.warn("Thread count is greater than the number of available processors, lowering -t, --threads equal to or below {} is recommended",
                            Runtime.getRuntime().availableProcessors());
                }
            } catch (NumberFormatException e) {
                logErrorAndExit("Invalid thread count", true);
            }
        }

        if (!ValidationUtils.isValidZoom(cmd.getOptionValue("s"))) {
            logErrorAndExit("Invalid start zoom", true);
            return;
        } else {
            startZoom = Integer.parseInt(cmd.getOptionValue("s"));
        }

        if (!ValidationUtils.isValidZoom(cmd.getOptionValue("e"))) {
            logErrorAndExit("Invalid end zoom", true);
            return;
        } else {
            endZoom = Integer.parseInt(cmd.getOptionValue("e"));
        }

        if (startZoom > endZoom) {
            logErrorAndExit("Start zoom must be less than or equal to end zoom", false);
        }

        logger.info("Starting download...");
        Download.startDownloading(region, layer, url, override, threads, startZoom, endZoom, geopackagePath);
    }

    private static void processFix(CommandLine cmd, String filename) throws SQLException, IOException, FactoryException {
        String url = null;
        if (cmd.hasOption("u")) {
            url = cmd.getOptionValue("u");
        }

        String jdbcUrl = "jdbc:sqlite:" + filename;
        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            List<Download.FailedTileDownload> fails = SQLUtils.getFailedTileDownloads(conn);
            if (fails.isEmpty()) {
                logger.info("No failed tile downloads to fix");
                return;
            }

            logger.info("Starting to fix tiles...");
            try (GeoPackage gpkg = new GeoPackage(new File(filename))) {
                TileEntry[] entries = new TileEntry[fails.size()];

                int i = 0;
                for (Download.FailedTileDownload fail : fails) {
                    entries[i] = Download.initGeopackage(gpkg, fail.layer());
                    Download.createTileMatrices(
                            fail.layer(),
                            entries[i],
                            fail.tile().zoom(),
                            fail.tile().zoom(),
                            conn
                    );
                    i++;
                }

                List<Download.FailedTileDownload> newFails = new ArrayList<>();

                try (ProgressBar pb = new ProgressBarBuilder()
                        .setInitialMax(fails.size())
                        .setTaskName("Re-downloading tiles")
                        .setStyle(ProgressBarStyle.ASCII)
                        .build()
                ) {
                    for (Download.FailedTileDownload fail : fails) {
                        try {
                            String finalUrl = url != null ? url : fail.url();
                            if (finalUrl == null) {
                                logger.error("No URL to default to for tile (z: {}, x: {}, y: {}) (use -u, --url)",
                                        fail.tile().zoom(), fail.tile().x(), fail.tile().y());
                                return;
                            }

                            byte[] image = TileUtils.downloadTileWithRetries(
                                    fail.tile(),
                                    finalUrl,
                                    Constants.TILE_DOWNLOAD_ATTEMPTS,
                                    Constants.DOWNLOAD_RETRY_DELAY_MS
                            );

                            boolean addedTile = false;
                            for (TileEntry entry : entries) {
                                if (entry.getTableName().equals(fail.layer())) {
                                    SQLUtils.updateTile(new Tile(
                                            fail.tile().zoom(),
                                            fail.tile().x(),
                                            fail.tile().y(),
                                            image
                                    ), fail.layer(), conn);
                                    addedTile = true;
                                    break;
                                }
                            }

                            if (!addedTile) throw new Exception("Could not find tile entry for layer \"" + fail.layer() + "\"");
                            pb.step();
                        } catch (Exception e) {
                            if (e instanceof InterruptedException) {
                                Thread.currentThread().interrupt();
                            }
                            Download.FailedTileDownload newFail = new Download.FailedTileDownload(
                                    fail.tile(),
                                    fail.layer(),
                                    fail.url(),
                                    e
                            );
                            newFails.add(newFail);
                        }
                    }
                }

                System.out.println();
                SQLUtils.clearFailedTileDownloads(conn);
                for (Download.FailedTileDownload fail : newFails) {
                    Download.logFailedTileDownload(fail);
                    SQLUtils.addFailedTileDownload(fail, conn);
                }
                if (!newFails.isEmpty()) System.out.println();

                logger.info("Finished re-downloading {}/{} tiles", fails.size() - newFails.size(), fails.size());
                logger.info("Failed downloads: {}", newFails.size());
            }
        }
    }

    private static void processClearErrors(String filename) throws SQLException {
        String jdbcUrl = "jdbc:sqlite:" + filename;
        try (Connection conn = DriverManager.getConnection(jdbcUrl)) {
            List<Download.FailedTileDownload> fails = SQLUtils.getFailedTileDownloads(conn);
            if (fails.isEmpty()) {
                logger.info("No failed tile downloads to clear");
                return;
            }

            SQLUtils.clearFailedTileDownloads(conn);
            logger.info("Cleared {} failed tile download error{} from database", fails.size(), fails.size() == 1 ? "" : "s");
        }
    }

    private static void processTileCount(CommandLine cmd) {
        if (!cmd.hasOption("r")) {
            logErrorAndExit("Missing required tile count options (-r, --region)", true);
        }

        if (!cmd.hasOption("s") || !cmd.hasOption("e")) {
            logErrorAndExit("Missing required zoom level options (-s, --start-zoom and -e, --end-zoom)", true);
        }

        String regionString = cmd.getOptionValue("r");
        Region region;

        try {
            region = Region.fromString(regionString);
            assert region != null;
        } catch (Exception e) {
            logErrorAndExit("Invalid region: {}", false, e.getMessage());
            return;
        }

        int startZoom;
        int endZoom;

        if (!ValidationUtils.isValidZoom(cmd.getOptionValue("s"))) {
            logErrorAndExit("Invalid start zoom", true);
            return;
        } else {
            startZoom = Integer.parseInt(cmd.getOptionValue("s"));
        }

        if (!ValidationUtils.isValidZoom(cmd.getOptionValue("e"))) {
            logErrorAndExit("Invalid end zoom", true);
            return;
        } else {
            endZoom = Integer.parseInt(cmd.getOptionValue("e"));
        }

        if (startZoom > endZoom) {
            logErrorAndExit("Start zoom must be less than or equal to end zoom", false);
        }

        logger.info("Calculating tile count, this may take some time...");

        int tileCount = 0;
        for (int zoom = startZoom; zoom <= endZoom; zoom++) {
            tileCount += TileUtils.findTilesInRegion(region, zoom).size();
        }

        logger.info("Tiles in region (zoom {}-{}): {}", startZoom, endZoom, tileCount);
    }

    private static Options createOptions() {
        Options options = new Options();

        options.addOption("h", "help", false, "Print this message");
        options.addOption("V", "version", false, "Print the program version");

        // Mutually exclusive base options
        options.addOption("d", "download", false, "Download tiles to geopackage");
        options.addOption("f", "fix", false, "Fix failed tile downloads");
        options.addOption(null, "clear-errors", false, "Clear all failed tile download errors from database");
        options.addOption(null, "tile-count", false, "Calculate tile count in region");

        // Download options
        options.addOption("l", "layer", true, "Table in geopackage to store to");
        options.addOption("r", "region", true, "Region polygon(s) (format: wkt|shapefile:<path or string>, geopackage:<table>@<path>)");
        options.addOption("u", "url", true, "Tile URL for tiles (must include {x}, {y}, and {z} as placeholders)");
        options.addOption("o", "override", false, "Override existing tiles while downloading (default: false)");
        options.addOption("t", "threads", true, "Thread count for multithreaded downloading (default: all available)");
        options.addOption("s", "start-zoom", true, "Start zoom level (0-30 inclusive)");
        options.addOption("e", "end-zoom", true, "End zoom level (0-30 inclusive)");

        return options;
    }

    private static void logErrorAndExit(String message, boolean printHelp, Object... args) {
        logger.error(message, args);
        if (printHelp) printHelp(createOptions());
        System.exit(1);
    }
}