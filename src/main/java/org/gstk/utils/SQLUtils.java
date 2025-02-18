package org.gstk.utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SQLUtils {
    public static boolean checkTileExists(TileUtils.Tile tile, String layer, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                """
                SELECT png_data
                FROM tiles
                WHERE layer = ? AND zoom = ? AND tile_x = ? AND tile_y = ?
                LIMIT 1
                """)) {
            ps.setString(1, layer);
            ps.setInt(2, tile.zoom());
            ps.setInt(3, tile.x());
            ps.setInt(4, tile.y());

            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static void storeTile(TileUtils.Tile tile, String layer, byte[] pngData, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                """
                INSERT OR REPLACE INTO tiles (layer, zoom, tile_x, tile_y, png_data) VALUES (?, ?, ?, ?, ?)
                """)) {
            ps.setString(1, layer);
            ps.setInt(2, tile.zoom());
            ps.setInt(3, tile.x());
            ps.setInt(4, tile.y());
            ps.setBytes(5, pngData);
            ps.executeUpdate();
        }
    }

    public static byte[] retrieveTile(TileUtils.Tile tile, String layer, Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                """
                SELECT png_data
                FROM tiles
                WHERE layer = ? AND zoom = ? AND tile_x = ? AND tile_y = ?
                """)) {
            ps.setString(1, layer);
            ps.setInt(2, tile.zoom());
            ps.setInt(3, tile.x());
            ps.setInt(4, tile.y());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getBytes("png_data");
                }
            }
        }
        throw new SQLException("Failed to retrieve png_data");
    }
}