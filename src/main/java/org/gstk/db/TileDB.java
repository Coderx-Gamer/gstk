package org.gstk.db;

import org.gstk.utils.TileUtils.TileData;
import org.gstk.utils.TileUtils.TilePosition;
import org.gstk.utils.ValidationUtils;

import java.sql.SQLException;

public interface TileDB {
    String getIdentifier();
    boolean isConnected();

    void init() throws Exception;
    boolean needsInitForZoomLevels();
    default void initForZoomLevels(int startZoom, int endZoom) throws Exception {
        ValidationUtils.checkValidZoomLevels(String.valueOf(startZoom), String.valueOf(endZoom));
    }

    void storeTile(TileData tile) throws SQLException;
    boolean doesTileExist(int column, int row, int zoom) throws SQLException;

    default boolean doesTileExist(TilePosition pos) throws SQLException {
        return doesTileExist(pos.x(), pos.y(), pos.zoom());
    }

    void close();

    static TileDB open(String id) throws InitException {
        if (id.startsWith("gpkg:")) {
            return new GeoPackageDB(id.substring("gpkg:".length()));
        }
        throw new InitException("Invalid database identifier");
    }

    class InitException extends RuntimeException {
        public InitException(String message) {
            super(message);
        }

        public InitException(Exception e) {
            super(e);
        }
    }
}
