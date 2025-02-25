# GSTK (GIS Toolkit)

GSTK is a lightweight Java CLI tool for downloading tiles from GIS servers onto
a local SQLite database (`.db` file) which can be hosted locally.

This database can be inspected using any SQLite viewer or queried via `sqlite3`.

This tool is useful for using maps offline (e.g., in remote areas, where internet is unreliable).

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

**Note: Pre-built jar files are available in the releases page.**

## Usage

To see the usage for GSTK, run `java -jar <gstk jar file> --help`, and
to see the current version, run `java -jar <gstk jar file> --version`.

## Examples

Download a subset of Los Angeles, California to a database:
```bash
java -jar gstk.jar \
  --download \
  --region '34.096501,-118.314534;34.049519,-118.185870;34.008607,-118.286687' \
  --layer 'OpenStreetMap' \
  --url 'https://tile.openstreetmap.org/{z}/{x}/{y}.png' \
  --start-zoom 0 \
  --end-zoom 18 \
  los-angeles-osm_0-18.db
```

Host a GIS database:
```bash
java -jar gstk.jar \
  --host \
  --layer 'OpenStreetMap' \
  --port 8080 \
  los-angeles-osm_0-18.db
```

## Updating

To update the version before releasing a new build:
1. Update the `pom.xml` version (`<version>...</version>`).
2. Update `PROJECT_VERSION` in `org.gstk.Constants`.
3. Rebuild the project with `mvn clean package`.

## License

This project is licensed under the [MIT License](https://opensource.org/license/mit).