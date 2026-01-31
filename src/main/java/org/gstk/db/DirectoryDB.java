package org.gstk.db;

import org.gstk.Region;
import org.gstk.utils.TileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DirectoryDB implements TileDB {
    private final File dir;

    public DirectoryDB(String dirPath) throws InitException {
        dir = new File(dirPath);
        if (dir.exists() && !dir.isDirectory()) {
            throw new InitException("Provided path is not a directory");
        }
    }

    @Override
    public void close() {}

    @Override
    public String getIdentifier() {
        return "dir:" + dir.getAbsolutePath();
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public void init() throws IOException {
        if (!dir.exists()) {
            Files.createDirectory(dir.toPath());
        }
    }

    @Override
    public boolean needsAdvancedInit() {
        return false;
    }

    @Override
    public void advancedInit(int startZoom, int endZoom, Region region) {}

    @Override
    public void storeTile(TileUtils.TileData tile) throws IOException {
        Path tileDirectory = dir.toPath()
            .resolve(String.valueOf(tile.pos().zoom()))
            .resolve(String.valueOf(tile.pos().x()));
        if (Files.exists(tileDirectory) && !Files.isDirectory(tileDirectory)) {
            throw new IOException("Path " + tileDirectory + " is occupied by a non-directory");
        }
        if (!Files.exists(tileDirectory)) {
            Files.createDirectories(tileDirectory);
        }

        Path tilePath = tileDirectory.resolve(String.valueOf(tile.pos().y()));
        Files.write(tilePath, tile.data());
    }

    @Override
    public boolean doesTileExist(int column, int row, int zoom) {
        Path tilePath = dir.toPath()
            .resolve(String.valueOf(zoom))
            .resolve(String.valueOf(column))
            .resolve(String.valueOf(row));
        return Files.exists(tilePath) && !Files.isDirectory(tilePath);
    }
}
