package com.onthegomap.planetiler.openmaptiles;

import static com.onthegomap.planetiler.TestUtils.assertContains;
import static com.onthegomap.planetiler.TestUtils.assertFeatureNear;
import static com.onthegomap.planetiler.openmaptiles.util.VerifyMonaco.MONACO_BOUNDS;
import static com.onthegomap.planetiler.util.Gzip.gunzip;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.onthegomap.planetiler.TestUtils;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.config.Arguments;
import com.onthegomap.planetiler.mbtiles.Mbtiles;
import com.onthegomap.planetiler.openmaptiles.util.VerifyMonaco;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.LineString;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.Polygon;

/**
 * End-to-end tests for OpenMapTiles generation.
 * <p>
 * Generates an entire map for the smallest openstreetmap extract available (Monaco) and asserts that expected output
 * features exist
 */
class OpenMapTilesTest {

  @TempDir
  static Path tmpDir;
  private static Mbtiles mbtiles;

  private static Path getTestResource(String name) throws IOException {
    var path = tmpDir.resolve(name);
    try (
      var input = OpenMapTilesTest.class.getResourceAsStream("/" + name);
      var output = Files.newOutputStream(path);
    ) {
      Objects.requireNonNull(input, "Could not find " + name + " on classpath").transferTo(output);
    }
    return path;
  }

  @BeforeAll
  public static void runPlanetiler() throws Exception {
    Path dbPath = tmpDir.resolve("output.mbtiles");
    var osmPath = getTestResource("monaco-latest.osm.pbf");
    var naturalEarthPath = getTestResource("natural_earth_vector.sqlite.zip");
    var waterPath = getTestResource("water-polygons-split-3857.zip");
    OpenMapTilesMain.run(Arguments.of(
      // Override input source locations
      "osm_path", osmPath,
      "natural_earth_path", naturalEarthPath,
      "water_polygons_path", waterPath,
      // no centerlines in monaco - so fake it out with an empty source
      "lake_centerlines_path", waterPath,

      // Override temp dir location
      "tmp", tmpDir.toString(),

      // Override output location
      "mbtiles", dbPath.toString()
    ));
    Files.delete(osmPath);
    Files.delete(naturalEarthPath);
    Files.delete(waterPath);

    mbtiles = Mbtiles.newReadOnlyDatabase(dbPath);
  }

  @AfterAll
  public static void close() throws IOException {
    mbtiles.close();
  }

  @Test
  void testMetadata() {
    Map<String, String> metadata = mbtiles.metadata().getAll();
    assertEquals("OpenMapTiles", metadata.get("name"));
    assertEquals("0", metadata.get("minzoom"));
    assertEquals("14", metadata.get("maxzoom"));
    assertEquals("baselayer", metadata.get("type"));
    assertEquals("pbf", metadata.get("format"));
    assertEquals("7.40921,43.72335,7.44864,43.75169", metadata.get("bounds"));
    assertEquals("7.42892,43.73752,14", metadata.get("center"));
    assertContains("openmaptiles.org", metadata.get("description"));
    assertContains("openmaptiles.org", metadata.get("attribution"));
    assertContains("www.openstreetmap.org/copyright", metadata.get("attribution"));
  }

  @Test
  void ensureValidGeometries() throws Exception {
    Set<Mbtiles.TileEntry> parsedTiles = TestUtils.getAllTiles(mbtiles);
    for (var tileEntry : parsedTiles) {
      var decoded = VectorTile.decode(gunzip(tileEntry.bytes()));
      for (VectorTile.Feature feature : decoded) {
        TestUtils.validateGeometry(feature.geometry().decode());
      }
    }
  }

  @Test
  void testContainsOceanPolyons() {
    assertFeatureNear(mbtiles, "water", Map.of(
      "class", "ocean"
    ), 7.4484, 43.70783, 0, 14);
  }

  @Test
  void testContainsCountryName() {
    assertFeatureNear(mbtiles, "place", Map.of(
      "class", "country",
      "iso_a2", "MC",
      "name", "Monaco"
    ), 7.42769, 43.73235, 2, 14);
  }

  @Test
  void testContainsSuburb() {
    assertFeatureNear(mbtiles, "place", Map.of(
      "name", "Les Moneghetti",
      "class", "suburb"
    ), 7.41746, 43.73638, 11, 14);
  }

  @Test
  void testContainsBuildings() {
    assertFeatureNear(mbtiles, "building", Map.of(), 7.41919, 43.73401, 13, 14);
    assertNumFeatures("building", Map.of(), 14, 1316, Polygon.class);
    assertNumFeatures("building", Map.of(), 13, 196, Polygon.class);
  }

  @Test
  void testContainsHousenumber() {
    assertFeatureNear(mbtiles, "housenumber", Map.of(
      "housenumber", "27"
    ), 7.42117, 43.73652, 14, 14);
    assertNumFeatures("housenumber", Map.of(), 14, 274, Point.class);
  }

  @Test
  void testBoundary() {
    assertFeatureNear(mbtiles, "boundary", Map.of(
      "admin_level", 2L,
      "maritime", 1L,
      "disputed", 0L
    ), 7.41884, 43.72396, 4, 14);
  }

  @Test
  void testAeroway() {
    assertNumFeatures("aeroway", Map.of(
      "class", "heliport"
    ), 14, 1, Polygon.class);
    assertNumFeatures("aeroway", Map.of(
      "class", "helipad"
    ), 14, 11, Polygon.class);
  }

  @Test
  void testLandcover() {
    assertNumFeatures("landcover", Map.of(
      "class", "grass",
      "subclass", "park"
    ), 14, 20, Polygon.class);
    assertNumFeatures("landcover", Map.of(
      "class", "grass",
      "subclass", "garden"
    ), 14, 33, Polygon.class);
  }

  @Test
  void testPoi() {
    assertNumFeatures("poi", Map.of(
      "class", "restaurant",
      "subclass", "restaurant"
    ), 14, 217, Point.class);
    assertNumFeatures("poi", Map.of(
      "class", "art_gallery",
      "subclass", "artwork"
    ), 14, 132, Point.class);
  }

  @Test
  void testLanduse() {
    assertNumFeatures("landuse", Map.of(
      "class", "residential"
    ), 14, 8, Polygon.class);
    assertNumFeatures("landuse", Map.of(
      "class", "hospital"
    ), 14, 4, Polygon.class);
  }

  @Test
  void testTransportation() {
    assertNumFeatures("transportation", Map.of(
      "class", "path",
      "subclass", "footway"
    ), 14, 909, LineString.class);
    assertNumFeatures("transportation", Map.of(
      "class", "primary"
    ), 14, 170, LineString.class);
  }

  @Test
  void testTransportationName() {
    assertNumFeatures("transportation_name", Map.of(
      "name", "Boulevard du Larvotto",
      "class", "primary"
    ), 14, 12, LineString.class);
  }

  @Test
  void testWaterway() {
    assertNumFeatures("waterway", Map.of(
      "class", "stream"
    ), 14, 6, LineString.class);
  }

  @TestFactory
  Stream<DynamicTest> testVerifyChecks() {
    return VerifyMonaco.verify(mbtiles).results().stream()
      .map(check -> dynamicTest(check.name(), () -> {
        check.error().ifPresent(Assertions::fail);
      }));
  }

  private static void assertNumFeatures(String layer, Map<String, Object> attrs, int zoom,
    int expected, Class<? extends Geometry> clazz) {
    TestUtils.assertNumFeatures(mbtiles, layer, zoom, attrs, MONACO_BOUNDS, expected, clazz);
  }
}
