package org.gstk;

import jakarta.xml.bind.JAXBException;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.gstk.db.TileDB;
import org.gstk.utils.TileUtils;
import org.gstk.utils.ValidationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Scanner;

public class Main {
    public static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) {
        Options options = createOptions();

        CommandLineParser parser = new DefaultParser();
        CommandLine cmd;

        try {
            cmd = parser.parse(options, args);
            if (cmd.getOptions().length == 0 || cmd.hasOption("h")) {
                printHelp(options);
                System.exit(0);
            }

            if (cmd.hasOption("V")) {
                printVersion();
                System.exit(0);
            }

            if (cmd.hasOption("d") || cmd.hasOption("tile-count")) {
                try {
                    ValidationUtils.checkValidZoomLevels(cmd.getOptionValue("s"), cmd.getOptionValue("e"));
                } catch (ValidationUtils.InvalidZoomException e) {
                    logErrorAndExit("Invalid zoom levels", false, e);
                }
            }

            if (cmd.hasOption("d")) {
                processDownload(cmd);
            } else if (cmd.hasOption("f")) {
                processFix(cmd);
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
            Usage: java -jar ... [options...]

            Options:
              -h, --help          %s
              -V, --version       %s

            Mutually exclusive:
              -d, --download      %s
              -f, --fix           %s
              --tile-count        %s

            Download (-d, --download) options:
              -D  --db            %s
              -r, --region        %s
              -u, --url           %s
              -F, --fails-file    %s
              -o, --override      %s
              -t, --threads       %s

              -s, --start-zoom    %s
              -e, --end-zoom      %s

            Fix (-f, --fix) options:
              -F, --fails-file    %s
              -D, --db            %s

            Tile count (--tile-count) options:
              -r, --region        %s

              -s, --start-zoom    %s
              -e, --end-zoom      %s
            """,
            options.getOption("h").getDescription(),
            options.getOption("V").getDescription(),
            options.getOption("d").getDescription(),
            options.getOption("f").getDescription(),
            options.getOption("tile-count").getDescription(),
            options.getOption("D").getDescription(),
            options.getOption("r").getDescription(),
            options.getOption("u").getDescription(),
            options.getOption("F").getDescription(),
            options.getOption("o").getDescription(),
            options.getOption("t").getDescription(),
            options.getOption("s").getDescription(),
            options.getOption("e").getDescription(),
            options.getOption("F").getDescription(),
            options.getOption("D").getDescription(),
            options.getOption("r").getDescription(),
            options.getOption("s").getDescription(),
            options.getOption("e").getDescription()
        );
    }

    private static void printVersion() {
        System.out.println("GSTK " + Constants.PROJECT_VERSION);
    }

    private static void processDownload(CommandLine cmd) {
        if (!cmd.hasOption("D") ||
            !cmd.hasOption("r") ||
            !cmd.hasOption("u"))
        {
            logErrorAndExit("Missing required download options", true);
        }

        if (!cmd.hasOption("s") || !cmd.hasOption("e")) {
            logErrorAndExit("Missing required zoom level options (-s, --start-zoom and -e, --end-zoom)", true);
        }

        String dbId = cmd.getOptionValue("D");
        String url;
        boolean override = cmd.hasOption("o");
        int threads = 4;
        int startZoom = Integer.parseInt(cmd.getOptionValue("s"));
        int endZoom = Integer.parseInt(cmd.getOptionValue("e"));

        String regionString = cmd.getOptionValue("r");
        Region region;

        try {
            region = Region.fromString(regionString);
        } catch (Exception e) {
            logErrorAndExit("Invalid region: {}", false, e.getMessage());
            return;
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
                    LOGGER.warn("Thread count is greater than the number of available processors, lowering -t, --threads equal to or below {} is recommended",
                        Runtime.getRuntime().availableProcessors());
                }
            } catch (NumberFormatException e) {
                logErrorAndExit("Invalid thread count", true);
            }
        }

        LOGGER.info("Opening database {}", dbId);

        TileDB db = null;
        try {
            db = TileDB.open(dbId);
            db.init();
            if (db.needsInitForZoomLevels()) {
                db.initForZoomLevels(startZoom, endZoom);
            }
        } catch (TileDB.InitException e) {
            logErrorAndExit("Failed to open database", false, e);
        } catch (Exception e) {
            logErrorAndExit("Failed to initialize database", false, e);
        }
        assert db != null;

        if (!db.isConnected()) {
            logErrorAndExit("Failed to connect to database", false);
        }

        File failsFile = getFailsFile(cmd, false);
        Downloader downloader = new Downloader(db, region, url, threads, failsFile);

        LOGGER.info("Beginning download...");
        downloader.start(startZoom, endZoom, override);
        db.close();

        LOGGER.info("Finished downloading tiles");
        LOGGER.info("New failed tile downloads: {}", downloader.failedTileCount);
        if (downloader.fails != null) {
            LOGGER.info("Total failed tile downloads: {}", downloader.fails.fails.fails.size());
        }
        if ((downloader.fails != null && !downloader.fails.fails.fails.isEmpty()) ||
            downloader.failedTileCount > 0)
        {
            LOGGER.info("To repair failed tile downloads, run java -jar ... --fix");
            LOGGER.info("Failed tile download data is stored in {}", failsFile.getName());
        }
    }

    private static void processFix(CommandLine cmd) {
        File failsFile = getFailsFile(cmd, true);
        String dbId = cmd.hasOption("D") ? cmd.getOptionValue("D") : "";

        try {
            FailedTiles failedTiles = new FailedTiles(failsFile, dbId);
            if (!dbId.isEmpty() && !dbId.equals(failedTiles.dbId)) {
                LOGGER.warn("The fails file database id does not match the command-line database id");
                LOGGER.warn("You might accidentally write to the wrong database");
                System.out.print("Continue? [Y/n]: ");

                Scanner scanner = new Scanner(System.in);
                String continueStr = scanner.nextLine().trim().toLowerCase();
                boolean shouldContinue = continueStr.isEmpty() || continueStr.equals("y");

                if (!shouldContinue) {
                    System.exit(0);
                }
            }
            if (dbId.isEmpty()) {
                dbId = failedTiles.dbId;
            }

            TileDB db = TileDB.open(dbId);
            Downloader downloader = new Downloader(db, null, null, 1, failsFile);

            LOGGER.info("Starting repair...");
            downloader.repair();
        } catch (JAXBException e) {
            logErrorAndExit("Failed to read fails file", false, e);
        } catch (TileDB.InitException e) {
            logErrorAndExit("Failed to open database {}", false, dbId, e);
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

        int startZoom = Integer.parseInt(cmd.getOptionValue("s"));
        int endZoom = Integer.parseInt(cmd.getOptionValue("e"));

        LOGGER.info("Calculating tile count, this may take some time...");

        int tileCount = 0;
        for (int zoom = startZoom; zoom <= endZoom; zoom++) {
            tileCount += TileUtils.findTilesInRegion(region, zoom).size();
        }

        LOGGER.info("Tiles in region (zoom {}-{}): {}", startZoom, endZoom, tileCount);
    }

    private static Options createOptions() {
        Options options = new Options();

        options.addOption("h", "help", false, "Print this message");
        options.addOption("V", "version", false, "Print the program version");

        // Mutually exclusive base options
        options.addOption("d", "download", false, "Download tiles to database");
        options.addOption("f", "fix", false, "Re-download failed tile downloads");
        options.addOption(null, "tile-count", false, "Calculate tile count in region");

        // Download options
        options.addOption("D", "db", true, "Database to store tiles to (format: gpkg:layer@file)");
        options.addOption("u", "url", true, "Tile URL for tiles (must include {x}, {y}, and {z} as placeholders)");
        options.addOption("o", "override", false, "Override existing tiles while downloading (default: false)");
        options.addOption("t", "threads", true, "Thread count for multi-threaded downloading (default: 4)");

        // Common options
        options.addOption("r", "region", true, "Region polygon(s) (format: wkt:<wkt string>, shp:<shp file>, gpkg:<table>@<path>)");
        options.addOption("s", "start-zoom", true, "Start zoom level (0-30 inclusive)");
        options.addOption("e", "end-zoom", true, "End zoom level (0-30 inclusive)");
        options.addOption("F", "fails-file", true, "File to store failed tile downloads to (default: gstk_failed_tiles.xml)");

        return options;
    }

    private static File getFailsFile(CommandLine cmd, boolean cancelOnNotExists) {
        String failsFilename = "gstk_failed_tiles.xml";
        if (cmd.hasOption("F")) {
            failsFilename = cmd.getOptionValue("F");
        }
        File failsFile = new File(failsFilename);
        if (cancelOnNotExists && !failsFile.exists()) {
            LOGGER.info("No failed tile downloads to fix");
            System.exit(0);
        }
        if (!failsFile.canWrite() || !failsFile.canRead()) {
            logErrorAndExit("Fails file {} has insufficient permissions", false, failsFilename);
        }
        return failsFile;
    }

    private static void logErrorAndExit(String message, boolean printHelp, Object... args) {
        LOGGER.error(message, args);
        if (printHelp) printHelp(createOptions());
        System.exit(1);
    }
}
