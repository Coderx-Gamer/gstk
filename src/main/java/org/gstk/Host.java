package org.gstk;

import com.sun.net.httpserver.HttpServer;
import org.gstk.utils.ImageUtils;
import org.gstk.utils.SQLUtils;
import org.gstk.utils.TileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

public class Host {
    private static final Logger logger = LoggerFactory.getLogger("GSTK");

    public static void startHosting(List<String> layers,
                                    String xyz,
                                    int port,
                                    String filename) {
        int xIndex = 1;
        int yIndex = 2;
        int zIndex = 0;

        String[] xyzParts = xyz.split("/");
        for (int i = 0; i < xyzParts.length; i++) {
            switch (xyzParts[i]) {
                case "{x}":
                    xIndex = i;
                    break;
                case "{y}":
                    yIndex = i;
                    break;
                case "{z}":
                    zIndex = i;
                    break;
            }
        }

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            final int finalXIndex = xIndex;
            final int finalYIndex = yIndex;
            final int finalZIndex = zIndex;

            String jdbcUrl = "jdbc:sqlite:" + filename;
            Connection conn = DriverManager.getConnection(jdbcUrl);

            server.createContext("/", exchange -> {
                String path = exchange.getRequestURI().getPath();
                String[] pathParts = path.split("/");

                if (pathParts.length == 5) {
                    try {
                        String layerQueried = pathParts[1];
                        int x = Integer.parseInt(pathParts[finalXIndex + 2]);
                        int y = Integer.parseInt(pathParts[finalYIndex + 2]);
                        int z = Integer.parseInt(pathParts[finalZIndex + 2]);

                        if (layers.contains(layerQueried) || layers.contains("All")) {
                            TileUtils.Tile tile = new TileUtils.Tile(x, y, z);
                            if (SQLUtils.checkTileExists(tile, layerQueried, conn)) {
                                byte[] rawImage = SQLUtils.retrieveTile(tile, layerQueried, conn);
                                if (ImageUtils.isPng(rawImage)) {
                                    exchange.getResponseHeaders().set("Content-Type", "image/png");
                                    exchange.sendResponseHeaders(200, rawImage.length);

                                    try (OutputStream out = exchange.getResponseBody()) {
                                        out.write(rawImage);
                                    }
                                } else {
                                    String response = "Tile was not in png format";
                                    exchange.sendResponseHeaders(400, response.getBytes().length);

                                    try (OutputStream out = exchange.getResponseBody()) {
                                        out.write(response.getBytes());
                                    }
                                }
                            } else {
                                String response = "Tile does not exist";
                                exchange.sendResponseHeaders(400, response.getBytes().length);

                                try (OutputStream out = exchange.getResponseBody()) {
                                    out.write(response.getBytes());
                                }
                            }
                        } else {
                            String response = "Layer does not exist";
                            exchange.sendResponseHeaders(400, response.getBytes().length);

                            try (OutputStream out = exchange.getResponseBody()) {
                                out.write(response.getBytes());
                            }
                        }
                    } catch (NumberFormatException e) {
                        String response = "Invalid path, expected format: /" + xyz;
                        exchange.sendResponseHeaders(400, response.getBytes().length);

                        try (OutputStream out = exchange.getResponseBody()) {
                            out.write(response.getBytes());
                        }
                    } catch (SQLException e) {
                        String response = "Failed to retrieve tile";
                        exchange.sendResponseHeaders(400, response.getBytes().length);

                        try (OutputStream out = exchange.getResponseBody()) {
                            out.write(response.getBytes());
                        }
                    }
                } else {
                    String response = "Invalid path, expected format: <layer or \"All\">/" + xyz;
                    exchange.sendResponseHeaders(400, response.getBytes().length);

                    try (OutputStream out = exchange.getResponseBody()) {
                        out.write(response.getBytes());
                    }
                }
            });

            server.start();
            logger.info("Server is running on port {}", port);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    if (conn != null && !conn.isClosed()) {
                        conn.close();
                    }
                } catch (SQLException e) {
                    logger.warn("Failed to close db connection: {}", e.getMessage());
                }
                server.stop(0);
                logger.info("Server stopped");
            }));

            Thread.currentThread().join();
        } catch (IOException | InterruptedException | SQLException e) {
            logger.error(e.getMessage());
            System.exit(1);
        }
    }
}