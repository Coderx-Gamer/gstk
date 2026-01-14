# Changelog

## Version format
X.Y.Z
- X: Major update
- Y: Minor update
- Z: Bug fix or small change

## 3.0.0 - 2026-01-13
### Added:
- XML-based failed tile download storage.
- More descriptive logging.
- Checkstyle enforcer.

### Removed:
- --layer CLI option.
- --clear-errors CLI option.
- SQL-based failed tile download storage.

### Changed:
- Different output database format (--db).
- Refactored code to be more extensible later.
- Rewrote downloader.
- All tiles for a zoom level are no longer all in memory before writing.
- Default number of threads for a download is now 4.

### Fixed:
- Removed unused methods and tests.

## 2.0.2 - 2025-06-25
### Fixed:
- Call `DataStore.dispose()` after loading geopackage vector.

## 2.0.1 - 2025-06-25
### Fixed:
- Removed `-t` from tile count options in the help menu.
- Punctuate CHANGELOG.md properly.

## 2.0.0 - 2025-06-25
### Added:
- Geopackage support for downloading tiles into.
- Support for using a WKT string for `--region`.
- Support for using a shapefile for `--region`.
- Support for using a geopackage for `--region`.
- Support for one or more polygons and inner rings.
- Option to fix failed tile downloads with `--fix`.
- Option to clear failed tile download errors.
- Tile count calculator for `--region` at different zoom levels.
- Chunk-based tile saving.

### Removed:
- Support for the old database format.
- Support for old polygon strings.
- Web hosting for saved tiles.

### Fixed:
- Incorrect total tile count after download.
- Duplicate tiles being queued for download.
- Progress bars being interrupted by logs.
- Help menu being displayed for non-CLI related errors.

## 1.0 - 2025-02-24
- Initial release.
