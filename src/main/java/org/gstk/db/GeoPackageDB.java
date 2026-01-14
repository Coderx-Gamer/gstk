package org.gstk.db;

import org.geotools.geometry.jts.ReferencedEnvelope;
import org.geotools.geopkg.GeoPackage;
import org.geotools.geopkg.TileEntry;
import org.geotools.geopkg.TileMatrix;
import org.geotools.referencing.CRS;
import org.gstk.utils.TileUtils;
import org.gstk.utils.ValidationUtils;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.List;

public class GeoPackageDB implements TileDB {
    private final String identifier;

    private final File file;
    private final String layer;

    private boolean connected;
    private final Connection conn;
    private GeoPackage gpkg = null;

    public GeoPackageDB(String id) throws InitException {
        identifier = id;
        String[] parts = id.split("@");
        if (parts.length != 2) {
            throw new InitException("Invalid geopackage (format: layer@file)");
        }

        file = new File(parts[1]);
        layer = parts[0];

        if (!ValidationUtils.isValidLayerName(layer)) {
            throw new InitException("Invalid layer / table name");
        }

        String jdbcUrl = "jdbc:sqlite:" + parts[1];
        try {
            conn = DriverManager.getConnection(jdbcUrl);
            connected = !conn.isClosed();
        } catch (SQLException e) {
            throw new InitException(e);
        }
    }

    @Override
    public void close() {
        try {
            conn.close();
        } catch (Exception ignored) {
        } finally {
            connected = false;
        }
    }

    @Override
    public String getIdentifier() {
        return "gpkg:" + identifier;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void init() throws IOException {
        gpkg = new GeoPackage(file);
        gpkg.init();
    }

    @Override
    public boolean needsInitForZoomLevels() {
        return true;
    }

    @Override
    public void initForZoomLevels(int startZoom, int endZoom) throws Exception {
        TileDB.super.initForZoomLevels(startZoom, endZoom);

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

        double initialResolution = 156543.03392804097;
        for (int z = startZoom; z <= endZoom; z++) {
            int matrixBound = (int) Math.pow(2, z);
            double pixelSize = initialResolution / Math.pow(2, z);

            entry.getTileMatricies().add(new TileMatrix(
                z, matrixBound, matrixBound, 256, 256, pixelSize, pixelSize
            ));
        }

        clearTileMatrices();
        addTileMatrices(entry.getTileMatricies());

        addTileMatrixSet(
            entry.getBounds(),
            Integer.parseInt(CRS.toSRS(entry.getBounds().getCoordinateReferenceSystem(), true))
        );
    }

    @Override
    public synchronized void storeTile(TileUtils.TileData tile) throws SQLException {
        String sql =
            "INSERT OR REPLACE INTO " + layer + " (zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?)";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, tile.pos().zoom());
            ps.setInt(2, tile.pos().x());
            ps.setInt(3, tile.pos().y());
            ps.setBytes(4, tile.data());
            ps.executeUpdate();
        }
    }

    @Override
    public boolean doesTileExist(int column, int row, int zoom) throws SQLException {
        String sql = "SELECT * FROM " + layer + " WHERE zoom_level = ? AND tile_column = ? AND tile_row = ?";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, zoom);
            ps.setInt(2, column);
            ps.setInt(3, row);

            ResultSet rs = ps.executeQuery();
            return rs.next();
        }
    }

    private void addTileMatrices(List<TileMatrix> matrices) throws SQLException {
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
                """))
            {
                ps.setString(1, layer);
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

    private void clearTileMatrices() throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            """
            DELETE FROM gpkg_tile_matrix WHERE table_name = ?
            """))
        {
            ps.setString(1, layer);
            ps.executeUpdate();
        }
    }

    private void addTileMatrixSet(ReferencedEnvelope envelope, int srsId) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
            """
            INSERT OR REPLACE INTO gpkg_tile_matrix_set (table_name,
                srs_id,
                min_x,
                min_y,
                max_x,
                max_y)
                VALUES (?, ?, ?, ?, ?, ?)
            """))
        {
            ps.setString(1, layer);
            ps.setInt(2, srsId);
            ps.setDouble(3, envelope.getMinX());
            ps.setDouble(4, envelope.getMinY());
            ps.setDouble(5, envelope.getMaxX());
            ps.setDouble(6, envelope.getMaxY());
            ps.executeUpdate();
        }
    }
}
