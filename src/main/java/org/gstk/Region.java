package org.gstk;

import org.geotools.api.data.*;
import org.geotools.api.feature.simple.SimpleFeature;
import org.geotools.api.referencing.FactoryException;
import org.geotools.api.referencing.crs.CoordinateReferenceSystem;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.referencing.CRS;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.MultiPolygon;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record Region(MultiPolygon polygons) {
    private static final Logger LOGGER = LoggerFactory.getLogger(Region.class);

    public static Region fromWkt(String wkt) throws InvalidRegionException, ParseException {
        GeometryFactory gf = new GeometryFactory();
        WKTReader reader = new WKTReader(gf);

        List<Polygon> polygonList = new ArrayList<>();

        Geometry geometry = reader.read(wkt);
        if (geometry instanceof Polygon p) {
            polygonList.add(p);
        } else if (geometry instanceof MultiPolygon mp) {
            for (int i = 0; i < mp.getNumGeometries(); i++) {
                Polygon p = (Polygon) mp.getGeometryN(i);
                polygonList.add(p);
            }
        } else {
            throw new InvalidRegionException("Invalid WKT geometry, must be POLYGON or MULTIPOLYGON");
        }

        Polygon[] polygonArray = polygonList.toArray(new Polygon[0]);
        MultiPolygon polygons = new MultiPolygon(polygonArray, gf);

        return new Region(polygons);
    }

    public static Region fromShapefile(String shapefilePath) throws InvalidRegionException, IOException, FactoryException {
        if (!Files.exists(Paths.get(shapefilePath))) {
            throw new InvalidRegionException("Shapefile does not exist");
        }

        File file = new File(shapefilePath);
        FileDataStore store = FileDataStoreFinder.getDataStore(file);
        SimpleFeatureSource source = store.getFeatureSource();

        MultiPolygon polygons = collectPolygons(source);
        store.dispose();

        return new Region(polygons);
    }

    public static Region fromGeopackage(String geopackagePath, String layer) throws InvalidRegionException, IOException, FactoryException {
        File file = new File(geopackagePath);
        if (!file.exists() || file.isDirectory()) {
            throw new InvalidRegionException("Geopackage does not exist");
        }

        Map<String, Object> params = new HashMap<>();
        params.put("dbtype", "geopkg");
        params.put("database", file.getAbsolutePath());

        DataStore store = DataStoreFinder.getDataStore(params);
        SimpleFeatureSource source = store.getFeatureSource(layer);

        MultiPolygon polygons = collectPolygons(source);
        store.dispose();

        return new Region(polygons);
    }

    public static Region fromString(String regionString) throws InvalidRegionException, IOException, ParseException, FactoryException {
        String[] parts = regionString.split(":");

        if (parts.length < 2) {
            throw new InvalidRegionException("Unknown region type for region string");
        }

        String data = regionString.substring(parts[0].length() + 1);

        Type type = switch (parts[0]) {
            case "wkt" -> Type.WKT;
            case "shp" -> Type.SHAPEFILE;
            case "gpkg" -> Type.GEOPACKAGE;
            default -> throw new InvalidRegionException("Unknown region type for region string");
        };

        if (type.equals(Type.GEOPACKAGE)) {
            String[] geopackageParts = data.split("@");
            if (geopackageParts.length < 2) {
                throw new InvalidRegionException("Invalid geopackage region string");
            }

            String layer = geopackageParts[0];
            String path = data.substring(geopackageParts[0].length() + 1);

            return fromGeopackage(path, layer);
        }

        return switch (type) {
            case WKT -> fromWkt(data);
            case SHAPEFILE -> fromShapefile(data);
            default -> null;
        };
    }

    private static MultiPolygon collectPolygons(SimpleFeatureSource source) throws InvalidRegionException, IOException, FactoryException {
        CoordinateReferenceSystem crs = source.getSchema().getCoordinateReferenceSystem();
        if (crs != null) {
            int crsCode = CRS.lookupEpsgCode(crs, true);
            if (crsCode != 4326) {
                throw new InvalidRegionException("Region must be in WGS 84 (EPSG:4326), you are using EPSG:" + crsCode);
            }
        } else {
            LOGGER.warn("You are using an unknown CRS, please ensure you are using WGS 84 (EPSG:4326) coordinates");
        }

        List<Polygon> polygonList = new ArrayList<>();

        try (SimpleFeatureIterator iter = source.getFeatures().features()) {
            while (iter.hasNext()) {
                SimpleFeature feature = iter.next();
                Object geomObj = feature.getDefaultGeometry();

                if (geomObj instanceof Polygon p) {
                    polygonList.add(p);
                } else if (geomObj instanceof MultiPolygon mp) {
                    for (int i = 0; i < mp.getNumGeometries(); i++) {
                        Polygon p = (Polygon) mp.getGeometryN(i);
                        polygonList.add(p);
                    }
                }
            }
        }

        if (polygonList.isEmpty()) {
            throw new InvalidRegionException("No polygons found in source");
        }

        Polygon[] polygonArray = polygonList.toArray(new Polygon[0]);
        return new MultiPolygon(polygonArray, new GeometryFactory());
    }

    public enum Type {
        WKT,
        SHAPEFILE,
        GEOPACKAGE,
    }

    public static class InvalidRegionException extends RuntimeException {
        public InvalidRegionException(String message) {
            super(message);
        }
    }
}
