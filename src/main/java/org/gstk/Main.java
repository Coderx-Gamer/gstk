package org.gstk;

import org.apache.commons.cli.*;
import org.gstk.utils.ValidationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger("GSTK");

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

            if (cmd.hasOption("v")) {
                printVersion();
                System.exit(0);
            }

            if (cmd.hasOption("d") && cmd.hasOption("H")) {
                logger.error("Cannot run in both download and host mode");
                printHelp(options);
                System.exit(1);
            }

            String filename = null;

            List<String> indexedArgs = cmd.getArgList();
            if (indexedArgs.size() != 1 || indexedArgs.get(0).isEmpty()) {
                logger.error("Missing or invalid database file (index 0 parameter)");
                printHelp(options);
                System.exit(1);
            } else {
                filename = indexedArgs.get(0);
            }

            if (cmd.hasOption("d")) {
                if (!cmd.hasOption("r") ||
                    !cmd.hasOption("l") ||
                    !cmd.hasOption("u") ||
                    !cmd.hasOption("s") ||
                    !cmd.hasOption("e")) {
                    logger.error("Missing required download options");
                    printHelp(options);
                    System.exit(1);
                }

                String region;
                String layer;
                String url;
                boolean override;
                int threads = -1;
                int startZoom = -1;
                int endZoom = -1;

                if (!ValidationUtils.isValidRegion(region = cmd.getOptionValue("r"))) {
                    logger.error("Invalid region");
                    printHelp(options);
                    System.exit(1);
                }
                region = region.replace("\n", "").replace("\r", "").replace(" ", "");

                if (!ValidationUtils.isValidLayerName(layer = cmd.getOptionValue("l"))) {
                    logger.error("Invalid layer name");
                    printHelp(options);
                    System.exit(1);
                }

                if (!ValidationUtils.isValidGisUrl(url = cmd.getOptionValue("u"))) {
                    logger.error("Invalid GIS URL");
                    printHelp(options);
                    System.exit(1);
                }

                override = cmd.hasOption("o");

                if (cmd.hasOption("t")) {
                    try {
                        threads = Integer.parseInt(cmd.getOptionValue("t"));
                        if (threads < 1) {
                            throw new NumberFormatException();
                        }
                    } catch (NumberFormatException e) {
                        logger.error("Invalid number of threads");
                        printHelp(options);
                        System.exit(1);
                    }
                }

                if (!ValidationUtils.isValidZoom(cmd.getOptionValue("s"))) {
                    logger.error("Invalid start zoom");
                    printHelp(options);
                    System.exit(1);
                } else {
                    startZoom = Integer.parseInt(cmd.getOptionValue("s"));
                }

                if (!ValidationUtils.isValidZoom(cmd.getOptionValue("e"))) {
                    logger.error("Invalid end zoom");
                    printHelp(options);
                    System.exit(1);
                } else {
                    endZoom = Integer.parseInt(cmd.getOptionValue("e"));
                }

                if (startZoom > endZoom) {
                    logger.error("Start zoom must be less than or equal to end zoom");
                    printHelp(options);
                    System.exit(1);
                }

                logger.info("Starting download");
                Download.startDownloading(region, layer, url, override, threads, startZoom, endZoom, filename);
            } else if (cmd.hasOption("H")) {
                if (!cmd.hasOption("l")) {
                    logger.error("Missing required host options");
                    printHelp(options);
                    System.exit(1);
                }

                List<String> layers = new ArrayList<>();
                String xyz = "{z}/{x}/{y}";
                int port = 8080;

                String[] layersArray = cmd.getOptionValue("l").split(",");
                for (String layer : layersArray) {
                    if (!ValidationUtils.isValidLayerName(layer)) {
                        logger.error("Invalid layer name(s)");
                        printHelp(options);
                        System.exit(1);
                    } else {
                        layers.add(layer);
                    }
                }

                if (layers.isEmpty()) {
                    logger.error("Not enough layers specified");
                    printHelp(options);
                    System.exit(1);
                }

                if (cmd.hasOption("x")) {
                    if (!ValidationUtils.isValidXyz(xyz = cmd.getOptionValue("x"))) {
                        logger.error("Invalid xyz coordinate order");
                        printHelp(options);
                        System.exit(1);
                    }
                }

                if (cmd.hasOption("p")) {
                    if (!ValidationUtils.isValidPort(cmd.getOptionValue("p"))) {
                        logger.error("Invalid port number");
                        printHelp(options);
                        System.exit(1);
                    } else {
                        port = Integer.parseInt(cmd.getOptionValue("p"));
                    }
                }

                logger.info("Starting server");
                Host.startHosting(layers, xyz, port, filename);
            } else {
                logger.error("No mode specified (--download or --host)");
                printHelp(options);
                System.exit(1);
            }
        } catch (ParseException e) {
            logger.error(e.getMessage());
            printHelp(options);
            System.exit(1);
        }
    }

    private static void printHelp(Options options) {
        System.out.printf(
                """
                Usage: java -jar ... [options...] <database file>
                
                Options:
                  -h, --help        %s
                  -v, --version     %s
                
                  -d, --download    %s
                  -H, --host        %s
                
                Download (--download) options:
                  -r, --region      %s
                  -l, --layer       Layer name to save to
                  -u, --url         %s
                  -o, --override    %s
                  -t, --threads     %s
                
                  -s, --start-zoom  %s
                  -e, --end-zoom    %s
                
                Host (--host) options:
                  -l, --layer       Layer name(s) to host (format: "layer1,layer2,...")
                  -x, --xyz         %s
                  -p, --port        %s
                """,
                options.getOption("h").getDescription(),
                options.getOption("v").getDescription(),
                options.getOption("d").getDescription(),
                options.getOption("H").getDescription(),
                options.getOption("r").getDescription(),
                options.getOption("u").getDescription(),
                options.getOption("o").getDescription(),
                options.getOption("t").getDescription(),
                options.getOption("s").getDescription(),
                options.getOption("e").getDescription(),
                options.getOption("x").getDescription(),
                options.getOption("p").getDescription()
        );
    }

    private static void printVersion() {
        System.out.println("GSTK " + Constants.PROJECT_VERSION);
    }

    private static Options createOptions() {
        Options options = new Options();

        options.addOption("h", "help", false, "Print this message");
        options.addOption("v", "version", false, "Print the program version");
        options.addOption("d", "download", false, "Run in download mode (mutually exclusive with --host)");
        options.addOption("H", "host", false, "Run in host mode (mutually exclusive with --download)");

        // Download options
        options.addOption("r", "region", true, "Region polygon (format: lat,lon;lat,lon;...)");
        options.addOption("u", "url", true, "GIS URL for tiles (must include {x}, {y}, and {z} as placeholders)");
        options.addOption("o", "override", false, "Override existing tiles in database (default: false)");
        options.addOption("t", "threads", true, "Fix number of downloader threads (default: all available cores)");
        options.addOption("s", "start-zoom", true, "Start zoom level (0-30 inclusive)");
        options.addOption("e", "end-zoom", true, "End zoom level (0-30 inclusive)");

        // Host options
        options.addOption("x", "xyz", true, "Coordinate order for local GIS server (default / example: \"{z}/{x}/{y}\")");
        options.addOption("p", "port", true, "Port for local GIS server (default: 8080)");

        // Mutual options
        options.addOption("l", "layer", true, "Layer(s) in database");

        return options;
    }
}