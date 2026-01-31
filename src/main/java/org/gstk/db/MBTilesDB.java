package org.gstk.db;

import org.gstk.Region;
import org.gstk.utils.ImageUtils;
import org.gstk.utils.TileUtils;
import org.locationtech.jts.geom.*;

import java.io.File;
import java.sql.*;

public class MBTilesDB implements TileDB {
    private final File file;
    private final Connection conn;

    public MBTilesDB(String filename) throws InitException {
        this.file = new File(filename);
        if (file.exists() && (!file.canRead() || !file.canWrite())) {
            throw new InitException("Insufficient file permissions for " + file.getAbsolutePath());
        }

        String jdbcUrl = "jdbc:sqlite:" + filename;
        try {
            conn = DriverManager.getConnection(jdbcUrl);
            if (conn == null || conn.isClosed()) {
                throw new InitException("Failed to connect to database");
            }
            if (conn.isReadOnly()) {
                throw new InitException("Database is read-only");
            }
        } catch (SQLException e) {
            throw new InitException(e);
        }
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (Exception ignored) {}
    }

    @Override
    public String getIdentifier() {
        return "mbtiles:" + file.getAbsolutePath();
    }

    @Override
    public boolean isConnected() {
        try {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    public void init() throws SQLException {
        conn.setAutoCommit(false);
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS metadata (name TEXT UNIQUE, value TEXT)");
            stmt.executeUpdate(
                """
                CREATE TABLE IF NOT EXISTS tiles (
                    zoom_level  INTEGER,
                    tile_column INTEGER,
                    tile_row    INTEGER,
                    tile_data   BLOB
                )
                """
            );
            stmt.executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS tile_index ON tiles (zoom_level, tile_column, tile_row)");

            conn.commit();
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }

        updateMetadata("name", "GSTK Tiles");
        updateMetadata("format", "png");
    }

    @Override
    public boolean needsAdvancedInit() {
        return true;
    }

    @Override
    public void advancedInit(int startZoom, int endZoom, Region region) throws SQLException {
        String dbMinZoomStr = queryMetadata("minzoom");
        int dbMinZoom = dbMinZoomStr != null ? Integer.parseInt(dbMinZoomStr) : startZoom;

        String dbMaxZoomStr = queryMetadata("maxzoom");
        int dbMaxZoom = dbMaxZoomStr != null ? Integer.parseInt(dbMaxZoomStr) : endZoom;

        String dbBounds = queryMetadata("bounds");
        Polygon dbBoundsPoly = null;
        if (dbBounds != null) {
            String[] parts = dbBounds.split(",");
            if (parts.length != 4) {
                throw new IllegalStateException("Invalid MBTiles bounds metadata");
            }

            double minLon = Double.parseDouble(parts[0]);
            double minLat = Double.parseDouble(parts[1]);
            double maxLon = Double.parseDouble(parts[2]);
            double maxLat = Double.parseDouble(parts[3]);

            if (minLon > maxLon || minLat > maxLat ||
                minLon > 180.0  || minLon < -180.0 ||
                minLat > 90.0   || minLat < -90.0  ||
                maxLon > 180.0  || maxLon < -180.0 ||
                maxLat > 90.0   || maxLat < -90.0)
            {
                throw new IllegalStateException("Invalid MBTiles bounds metadata");
            }

            Coordinate[] vertices = new Coordinate[]{
                new Coordinate(minLon, minLat),
                new Coordinate(minLon, maxLat),
                new Coordinate(maxLon, maxLat),
                new Coordinate(maxLon, minLat),
                new Coordinate(minLon, minLat)
            };
            dbBoundsPoly = region.polygons().getFactory().createPolygon(vertices);
        }

        Geometry mergedGeom =
            dbBoundsPoly == null
            ? region.polygons()
            : region.polygons().union(dbBoundsPoly);

        Coordinate centroid = mergedGeom.getCentroid().getCoordinate();

        Envelope envelope = mergedGeom.getEnvelopeInternal();
        double minLon = envelope.getMinX();
        double minLat = envelope.getMinY();
        double maxLon = envelope.getMaxX();
        double maxLat = envelope.getMaxY();

        conn.setAutoCommit(false);
        try {
            updateMetadata("minzoom", String.valueOf(Math.min(dbMinZoom, startZoom)));
            updateMetadata("maxzoom", String.valueOf(Math.max(dbMaxZoom, endZoom)));
            updateMetadata("bounds", minLon + "," + minLat + "," + maxLon + "," + maxLat);

            int centerZoom = (Math.min(dbMinZoom, startZoom) + Math.max(dbMaxZoom, endZoom)) / 2;
            updateMetadata("center", centroid.x + "," + centroid.y + "," + centerZoom);

            conn.commit();
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    @Override
    public synchronized void storeTile(TileUtils.TileData tile) throws SQLException, IllegalArgumentException {
        if (!ImageUtils.isPng(tile.data())) {
            throw new IllegalArgumentException("Tile is not in png format");
        }

        try (PreparedStatement ps = conn.prepareStatement(
            """
            INSERT OR REPLACE INTO tiles (zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?)
            """))
        {
            int zoom = tile.pos().zoom();
            int x = tile.pos().x();
            int y = tile.pos().y();
            int tmsY = (1 << zoom) - 1 - y;

            ps.setInt(1, zoom);
            ps.setInt(2, x);
            ps.setInt(3, tmsY);
            ps.setBytes(4, tile.data());
            ps.executeUpdate();
        }
    }

    @Override
    public boolean doesTileExist(int column, int row, int zoom) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            """
            SELECT 1 FROM tiles WHERE zoom_level = ? AND tile_column = ? AND tile_row = ? LIMIT 1
            """))
        {
            ps.setInt(1, zoom);
            ps.setInt(2, column);

            int tmsRow = (1 << zoom) - 1 - row;
            ps.setInt(3, tmsRow);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private void updateMetadata(String name, String value) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            """
            INSERT OR REPLACE INTO metadata (name, value) VALUES (?, ?)
            """))
        {
            ps.setString(1, name);
            ps.setString(2, value);
            ps.executeUpdate();
        }
    }

    private String queryMetadata(String name) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            """
            SELECT value FROM metadata WHERE name = ? LIMIT 1
            """))
        {
            ps.setString(1, name);

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("value") : null;
            }
        }
    }
}
