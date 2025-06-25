package org.gstk.utils;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.geopkg.Tile;
import org.geotools.geopkg.TileMatrix;
import org.gstk.Download;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class SQLUtils {
    public static void updateTile(Tile tile, String table, Connection conn) throws SQLException, IllegalArgumentException {
        if (!table.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Invalid table name");
        }

        String sql = "INSERT OR REPLACE INTO " + table + " (zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tile.getZoom());
            ps.setInt(2, tile.getColumn());
            ps.setInt(3, tile.getRow());
            ps.setBytes(4, tile.getData());
            ps.executeUpdate();
        }
    }

    public static boolean doesTileExist(TileUtils.Tile tile, String table, Connection conn) throws SQLException, IllegalArgumentException {
        if (!table.matches("[a-zA-Z0-9_]+")) {
            throw new IllegalArgumentException("Invalid table name");
        }

        String sql = "SELECT * FROM " + table + " WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tile.zoom());
            ps.setInt(2, tile.x());
            ps.setInt(3, tile.y());

            ResultSet rs = ps.executeQuery();
            return rs.next();
        }
    }

    public static void addTileMatrices(List<TileMatrix> matrices, String table, Connection conn) throws SQLException {
        for (TileMatrix matrix : matrices) {
            try (PreparedStatement ps = conn.prepareStatement(
                    """
                    INSERT OR REPLACE INTO gpkg_tile_matrix (table_name,
                                                             zoom_level,
                                                             matrix_width,
                                                             matrix_height,
                                                             tile_width,
                                                             tile_height,
                                                             pixel_x_size,
                                                             pixel_y_size)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """
            )) {
                ps.setString(1, table);
                ps.setInt(2, matrix.getZoomLevel());
                ps.setInt(3, matrix.getMatrixWidth());
                ps.setInt(4, matrix.getMatrixHeight());
                ps.setInt(5, matrix.getTileWidth());
                ps.setInt(6, matrix.getTileHeight());
                ps.setDouble(7, matrix.getXPixelSize());
                ps.setDouble(8, matrix.getYPixelSize());
                ps.executeUpdate();
            }
        }
    }

    public static void clearTileMatrices(String table, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                """
                DELETE FROM gpkg_tile_matrix WHERE table_name = ?
                """
        )) {
            ps.setString(1, table);
            ps.executeUpdate();
        }
    }

    public static void addTileMatrixSet(ReferencedEnvelope envelope, int srsId, String table, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                """
                INSERT OR REPLACE INTO gpkg_tile_matrix_set (table_name,
                                                             srs_id,
                                                             min_x,
                                                             min_y,
                                                             max_x,
                                                             max_y)
                VALUES (?, ?, ?, ?, ?, ?)
                """
        )) {
            ps.setString(1, table);
            ps.setInt(2, srsId);
            ps.setDouble(3, envelope.getMinX());
            ps.setDouble(4, envelope.getMinY());
            ps.setDouble(5, envelope.getMaxX());
            ps.setDouble(6, envelope.getMaxY());
            ps.executeUpdate();
        }
    }

    public static void addFailedTileDownload(Download.FailedTileDownload fail, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                """
                INSERT INTO _gstk_failed_tile_downloads (layer, url, zoom_level, tile_column, tile_row)
                VALUES (?, ?, ?, ?, ?)
                """
        )) {
            ps.setString(1, fail.layer());
            ps.setString(2, fail.url());
            ps.setInt(3, fail.tile().zoom());
            ps.setInt(4, fail.tile().x());
            ps.setInt(5, fail.tile().y());
            ps.executeUpdate();
        }
    }

    public static List<Download.FailedTileDownload> getFailedTileDownloads(Connection conn) {
        List<Download.FailedTileDownload> fails = new ArrayList<>();

        try (PreparedStatement ps = conn.prepareStatement(
                """
                SELECT * FROM _gstk_failed_tile_downloads
                """);
             ResultSet rs = ps.executeQuery()
        ) {
            while (rs.next()) {
                fails.add(new Download.FailedTileDownload(
                        new TileUtils.Tile(
                                rs.getInt("tile_column"),
                                rs.getInt("tile_row"),
                                rs.getInt("zoom_level")
                        ),
                        rs.getString("layer"),
                        rs.getString("url"),
                        null
                ));
            }
        } catch (SQLException e) {
            return new ArrayList<>();
        }

        return fails;
    }

    public static void clearFailedTileDownloads(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                """
                DELETE FROM _gstk_failed_tile_downloads
                """
        )) {
            ps.executeUpdate();
        }
    }
}