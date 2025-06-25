# GSTK (GIS Toolkit)

GSTK is a lightweight Java CLI tool for downloading tiles from tile servers into a local geopackage. \
The outputted geopackage contains a raster tile table of the downloaded region.

This tool is useful for using maps offline (e.g., in remote areas, where the internet is unreliable).

## Prerequisites

Before using GSTK, ensure you have the following installed:
- **Java 17+** (required for running the application)
- **Apache Maven** (required for building from source)

## Building

To build the jar file for this project, open up a terminal and run these commands:
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
Usage: java -jar ... [options...] <geopackage>

Options:
  -h, --help          Print this message
  -V, --version       Print the program version

Mutually exclusive:
  -d, --download      Download tiles to geopackage
  -f, --fix           Fix failed tile downloads
  --clear-errors      Clear all failed tile download errors from database
  --tile-count        Calculate tile count in region

Download (-d, --download) options:
  -l, --layer         Table in geopackage to store to
  -r, --region        Region polygon(s) (format: wkt|shapefile:<path or string>, geopackage:<table>@<path>)
  -u, --url           Tile URL for tiles (must include {x}, {y}, and {z} as placeholders)
  -o, --override      Override existing tiles while downloading (default: false)
  -t, --threads       Thread count for multithreaded downloading (default: all available)

  -s, --start-zoom    Start zoom level (0-30 inclusive)
  -e, --end-zoom      End zoom level (0-30 inclusive)

Fix (-f, --fix) options:
  -u, --url           Tile URL for tiles (must include {x}, {y}, and {z} as placeholders) (optional)

Tile count (--tile-count) options:
  No file parameter required

  -r, --region        Region polygon(s) (format: wkt|shapefile:<path or string>, geopackage:<table>@<path>)

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
  --layer 'ExampleGIS' \
  --url 'https://www.examplegis.com/tile/{z}/{x}/{y}' \
  --start-zoom 0 \
  --end-zoom 18 \
  los-angeles_0-18.gpkg
```

With a shapefile:
```bash
java -jar gstk.jar \
  --download \
  --region 'shapefile:los_angeles.shp' \
  --layer 'ExampleGIS' \
  --url 'https://www.examplegis.com/tile/{z}/{x}/{y}' \
  --start-zoom 0 \
  --end-zoom 18 \
  los-angeles_0-18.gpkg
```

With a geopackage (vector):
```bash
java -jar gstk.jar \
  --download \
  --region 'geopackage:los_angeles@los_angeles.gpkg' \
  --layer 'ExampleGIS' \
  --url 'https://www.examplegis.com/tile/{z}/{x}/{y}' \
  --start-zoom 0 \
  --end-zoom 18 \
  los-angeles_0-18.gpkg
```

All inputted regions must use WGS 84 (latitude/longitude). \
When using WKT, longitude comes before latitude, and the first coordinates in a polygon must match the last coordinates to form a closed line string.

If you try to re-download the same region into a geopackage you already downloaded into, it will say it saved zero tiles. \
This is not a bug; it just means all the tiles were already present in the geopackage. \
If you want to re-download the old tiles, use the `--override` flag, and it will replace already present tiles instead of skipping them.

The `--url` flag follows this specification: <https://wiki.openstreetmap.org/wiki/Slippy_map_tilenames>

## Download Errors

If while you're downloading a geopackage and one or more tiles fail to download, you can fix it by using the `--fix` option:
```bash
java -jar gstk.jar --fix los-angeles_0-18.gpkg
```

If it returns an error for not having a URL to download from, or you want to re-download the broken tiles from a different URL,
use the `--url` flag with a working tile URL.

If you can't re-download the tiles, you can clear the failed download log by using `--clear-errors`:
```bash
java -jar gstk.jar --clear-errors los-angeles_0-18.gpkg
```

## Calculating tile count in a region

If you want to calculate the number of tiles that would be downloaded in a given `--region` in a zoom range (`--start-zoom` to `--end-zoom`),
without actually downloading it, you can use the `--tile-count` option:

```bash
java -jar gstk.jar --tile-count --start-zoom 0 --end-zoom 18 --region shapefile:los_angeles.shp
```

Note that if you're using a large region and high `--end-zoom`, this calculation could take a while.

## Updating

To update the version before releasing a new build:
1. Update the `pom.xml` version (`<version>...</version>`).
2. Check `mvn versions:display-dependency-updates` for dependency updates and update the code accordingly (don't use unstable updates).
3. Check `mvn versions:display-plugin-updates` for plugin updates and update the POM (don't use unstable updates).
4. Update `PROJECT_VERSION` in `org.gstk.Constants`.

## License

This project is licensed under the [MIT License](https://opensource.org/license/mit).