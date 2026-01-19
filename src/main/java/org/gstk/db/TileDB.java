package org.gstk.db;

import org.gstk.Region;
import org.gstk.utils.TileUtils.TileData;
import org.gstk.utils.TileUtils.TilePosition;

public interface TileDB {
    String getIdentifier();
    boolean isConnected();

    void init() throws Exception;
    boolean needsAdvancedInit();
    void advancedInit(
        int startZoom,
        int endZoom,
        Region region
    ) throws Exception;

    void storeTile(TileData tile) throws Exception;
    boolean doesTileExist(int column, int row, int zoom) throws Exception;

    default boolean doesTileExist(TilePosition pos) throws Exception {
        return doesTileExist(pos.x(), pos.y(), pos.zoom());
    }

    void close();

    static TileDB open(String id) throws InitException {
        if (id.startsWith("gpkg:")) {
            return new GeoPackageDB(id.substring("gpkg:".length()));
        }
        if (id.startsWith("mbtiles:")) {
            return new MBTilesDB(id.substring("mbtiles:".length()));
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
