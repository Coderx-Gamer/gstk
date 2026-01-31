# GSTK (GIS Toolkit)

GSTK is a lightweight Java CLI tool for downloading tiles from tile servers into a database.

This tool is useful for using maps offline (e.g., in remote areas, or where the internet is unreliable).

Supported output formats:
- GeoPackage (flag `--db gpkg:<layer>@<file>`)
- MBTiles (flag `--db mbtiles:<file>`)
- Directory (Z/X/Y) (flag `--db dir:<directory>`)

## Prerequisites

Before using GSTK, ensure you have the following installed:
- **Java 17+** (required for running the application)
- **Apache Maven** (required for building from source)

## Building

To build the executable jar file for this project, open up a terminal and run these commands:
```bash
git clone https://github.com/Coderx-Gamer/gstk.git
cd gstk
mvn clean package
```
The jar file should then be built to `target/gstk-<version>.jar`.

If you want to run JUnit tests, run `mvn clean test`.

**Note: Pre-built jar files are available on the release page.**

## Usage

To see the usage for GSTK, run `java -jar <gstk jar file> --help`. \
To see the current version, run `java -jar <gstk jar file> --version`.

```bash
java -jar gstk.jar --help
```
```
Usage: java -jar ... [options...]

Options:
  -h, --help          Print this message
  -V, --version       Print the program version

Mutually exclusive:
  -d, --download      Download tiles to database
  -f, --fix           Re-download failed tile downloads
  --tile-count        Estimate tile count in region

Download (-d, --download) options:
  -D, --db            Database to store tiles to (format: gpkg:<layer>@<file>, mbtiles:<file>, dir:<directory>)
  -r, --region        Region polygon(s) (format: bbox:<west>,<south>,<east>,<north>, wkt:<string>, shp:<file>, gpkg:<layer>@<file>)
  -u, --url           Tile URL to download from (must include {x}, {y}, and {z} as placeholders)
  -F, --fails-file    File to store failed tile downloads to (default: gstk_failed_tiles.xml) (set to "-" for no file)
  -o, --override      Override existing tiles in database (default: false)
  -t, --threads       Thread count for multi-threaded downloading (default: 4)
  -H, --headers       File with HTTP headers for requests (optional)
  --no-transcoding    Disable image type conversion before storing (default: false)

  -s, --start-zoom    Start zoom level (0-30 inclusive)
  -e, --end-zoom      End zoom level (0-30 inclusive)

Fix (-f, --fix) options:
  -D, --db            Database to store tiles to (format: gpkg:<layer>@<file>, mbtiles:<file>, dir:<directory>)
  -F, --fails-file    File to store failed tile downloads to (default: gstk_failed_tiles.xml)
  -H, --headers       File with HTTP headers for requests (optional)
  --no-transcoding    Disable image type conversion before storing (default: false)

Tile count (--tile-count) options:
  -p, --precise       Calculate the exact number of tiles (default: false)
  -r, --region        Region polygon(s) (format: bbox:<west>,<south>,<east>,<north>, wkt:<string>, shp:<file>, gpkg:<layer>@<file>)

  -s, --start-zoom    Start zoom level (0-30 inclusive)
  -e, --end-zoom      End zoom level (0-30 inclusive)
```

## Downloading

Here are some examples of the usage of the `--download` option.

Download a subset of Los Angeles, California to a geopackage with WKT:
```bash
java -jar gstk.jar \
  --download \
  --region 'wkt:POLYGON ((-118.314534 34.096501, -118.185870 34.049519, -118.286687 34.008607, -118.314534 34.096501))' \
  --url 'https://www.examplegis.com/tile/{z}/{x}/{y}' \
  --start-zoom 0 \
  --end-zoom 17 \
  --db gpkg:los_angeles@los-angeles_0-17.gpkg
```

With a shapefile:
```bash
java -jar gstk.jar \
  --download \
  --region 'shp:los_angeles.shp' \
  --url 'https://www.examplegis.com/tile/{z}/{x}/{y}' \
  --start-zoom 0 \
  --end-zoom 17 \
  --db gpkg:los_angeles@los-angeles_0-17.gpkg
```

With a geopackage (vector):
```bash
java -jar gstk.jar \
  --download \
  --region 'gpkg:los_angeles@los_angeles.gpkg' \
  --url 'https://www.examplegis.com/tile/{z}/{x}/{y}' \
  --start-zoom 0 \
  --end-zoom 17 \
  --db gpkg:los_angeles@los-angeles_0-17.gpkg
```

With a bounding box (Colorado):
```bash
java -jar gstk.jar \
  --download \
  --region 'bbox:-109,37,-102,41' \
  --url 'https://www.examplegis.com/tile/{z}/{x}/{y}' \
  --start-zoom 0 \
  --end-zoom 10 \
  --db gpkg:colorado@colorado_0-10.gpkg
```

After you start downloading, you will see progress bars for each zoom level and an ETA for their download time.

All input regions must use WGS 84 (latitude/longitude). \
When using WKT, longitude comes before latitude, and the first coordinates in a polygon must match the last coordinates to form a closed line string.

If you try to re-download the same region into a database you already downloaded into, it will say no tiles need to be downloaded. \
This is not a bug; it just means all the tiles were already present in the database. \
If you want to re-download the tiles, use the `--override` flag, and it will replace already present tiles instead of skipping them.

The `--url` flag follows this specification: <https://wiki.openstreetmap.org/wiki/Slippy_map_tilenames>

## Error Handling

By default, if GSTK fails to download or write a tile, it will save a list of those failed attempts in `gstk_failed_tiles.xml`. \
However, you can change `--fails-file` to change the path where it gets stored, and if `--fails-file` is set to `-`, it won't save fails anywhere.

If while you're downloading into a database and one or more tiles fail to download, you can fix it by using the `--fix` option:
```bash
# Fix using default fails file gstk_failed_tiles.xml
java -jar gstk.jar --fix --db gpkg:tiles@mytiles.gpkg

# If you changed --fails-file while using --download
java -jar gstk.jar --fix --fails-file example_errors.xml
```

## Calculating tile count in a region

If you want to calculate the number of tiles that would be downloaded in a given `--region` in a zoom range (`--start-zoom` to `--end-zoom`),
without actually downloading it, you can use the `--tile-count` option:

```bash
# Approximation
java -jar gstk.jar --tile-count --start-zoom 0 --end-zoom 18 --region shp:los_angeles.shp

# Exact
java -jar gstk.jar --tile-count --start-zoom 0 --end-zoom 18 --region shp:los_angeles.shp --precise
```

Note that if you're using `--precise`, the calculation could take a while.

## Updating

Before releasing a new build:
1. Update the `pom.xml` version (`<version>...</version>`).
2. Check `mvn versions:display-dependency-updates` for dependency updates and update the code accordingly (don't use unstable updates).
3. Check `mvn versions:display-plugin-updates` for plugin updates and update the POM (don't use unstable updates).
4. Update `PROJECT_VERSION` in `org.gstk.Constants`.

## License

This project is licensed under the [MIT License](https://opensource.org/license/mit).
