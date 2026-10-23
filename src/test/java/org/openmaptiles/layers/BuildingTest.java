package org.openmaptiles.layers;

import static com.onthegomap.planetiler.TestUtils.rectangle;

import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.config.Arguments;
import com.onthegomap.planetiler.config.PlanetilerConfig;
import com.onthegomap.planetiler.geo.GeoUtils;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SimpleFeature;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmReader;
import com.onthegomap.planetiler.stats.Stats;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.locationtech.jts.io.ParseException;
import org.locationtech.jts.io.WKTReader;
import org.openmaptiles.OpenMapTilesProfile;

class BuildingTest extends AbstractLayerTest {

  private final OpenMapTilesProfile profileHidingOutlinesWithParts = new OpenMapTilesProfile(translations,
    PlanetilerConfig.from(Arguments.of(Map.of(
      "building_hide_outlines_with_parts", "true"
    ))), Stats.inMemory());

  @Test
  void testBuilding() {
    assertFeatures(13, List.of(Map.of(
      "colour", "<null>",
      "hide_3d", "<null>",
      "_layer", "building",
      "_type", "polygon",
      "_minzoom", 13,
      "_maxzoom", 14,
      "_buffer", 4d,
      "_minpixelsize", 0.1d
    )), process(polygonFeature(Map.of(
      "building", "yes"
    ))));
    assertFeatures(13, List.of(Map.of(
      "_layer", "building",
      "_type", "polygon"
    )), process(polygonFeature(Map.of(
      "building:part", "yes"
    ))));
    assertFeatures(13, List.of(), process(polygonFeature(Map.of(
      "building", "no"
    ))));
  }

  @Test
  void testIgnoreUndergroundBuilding() {
    assertFeatures(14, List.of(), process(polygonFeature(Map.of(
      "building", "yes",
      "location", "underground"
    ))));
  }

  @Test
  void testAirportBuildings() {
    assertFeatures(13, List.of(Map.of(
      "_layer", "building",
      "_type", "polygon"
    )), process(polygonFeature(Map.of(
      "aeroway", "terminal"
    ))));
    assertFeatures(13, List.of(Map.of(
      "_layer", "building",
      "_type", "polygon"
    )), process(polygonFeature(Map.of(
      "aeroway", "hangar"
    ))));
  }

  @Test
  void testRenderHeights() {
    assertFeatures(13, List.of(Map.of(
      "render_height", "<null>",
      "render_min_height", "<null>"
    )), process(polygonFeature(Map.of(
      "building", "yes"
    ))));
    assertFeatures(14, List.of(Map.of(
      "render_height", 5,
      "render_min_height", 0
    )), process(polygonFeature(Map.of(
      "building", "yes"
    ))));
    assertFeatures(14, List.of(Map.of(
      "render_height", 12,
      "render_min_height", 3
    )), process(polygonFeature(Map.of(
      "building", "yes",
      "building:min_height", "3",
      "building:height", "12"
    ))));
    assertFeatures(14, List.of(Map.of(
      "render_height", 44,
      "render_min_height", 10
    )), process(polygonFeature(Map.of(
      "building", "yes",
      "building:min_level", "3",
      "building:levels", "12"
    ))));
    assertFeatures(14, List.of(), process(polygonFeature(Map.of(
      "building", "yes",
      "building:min_level", "1500",
      "building:levels", "1500"
    ))));
  }

  @ParameterizedTest
  @CsvSource({
    "2, 2",
    "2m, 2",
    "2 m, 2",
    "2.5 m, 3",
    "6', 2",
    "6'10\", 3",
  })
  void testRenderHeightUnits(String input, int expected) {
    assertFeatures(14, List.of(Map.of(
      "render_height", expected
    )), process(polygonFeature(Map.of(
      "building", "yes",
      "building:height", input
    ))));
  }

  @Test
  void testOutlineHides3d() {
    var relation = new OsmElement.Relation(1);
    relation.setTag("type", "building");

    var relationInfos = profile.preprocessOsmRelation(relation).stream()
      .map(i -> new OsmReader.RelationMember<>("outline", i)).toList();

    assertFeatures(14, List.of(Map.of(
      "_layer", "building",
      "hide_3d", true
    )), process(SimpleFeature.createFakeOsmFeature(
      GeoUtils.worldToLatLonCoords(rectangle(0, Math.sqrt(1))),
      Map.of(
        "building", "yes"
      ),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      0,
      relationInfos
    )));
  }

  @Test
  void testPartIsMarkedWhenHidingOutlinesWithParts() {
    var part = polygonFeature(Map.of(
      "building:part", "yes"
    ));
    var collector = featureCollectorFactory.get(part);
    profileHidingOutlinesWithParts.processFeature(part, collector);

    assertFeatures(14, List.of(Map.of(
      "_part", true
    )), collector);
    assertFeatures(14, List.of(Map.of(
      "_part", "<null>"
    )), process(part));
  }

  @Test
  void testOutlineMostlyCoveredByPartsHides3d() throws GeometryException {
    var outline = new VectorTile.Feature(
      Building.LAYER_NAME,
      1,
      VectorTile.encodeGeometry(rectangle(10, 20)),
      new HashMap<>(Map.of("render_height", 30)),
      0
    );
    var part = new VectorTile.Feature(
      Building.LAYER_NAME,
      2,
      VectorTile.encodeGeometry(rectangle(10, 10, 20, 18)),
      new HashMap<>(Map.of("render_height", 20, "_part", true)),
      0
    );
    var neighbor = new VectorTile.Feature(
      Building.LAYER_NAME,
      3,
      VectorTile.encodeGeometry(rectangle(20, 10, 30, 20)),
      new HashMap<>(Map.of("render_height", 10)),
      0
    );

    var result = profileHidingOutlinesWithParts.postProcessLayerFeatures(Building.LAYER_NAME, 14,
      List.of(outline, part, neighbor));

    Assertions.assertEquals(List.of(
      Map.of("render_height", 30, "hide_3d", true),
      Map.of("render_height", 20),
      Map.of("render_height", 10)
    ), result.stream().map(VectorTile.Feature::tags).toList());
  }

  @Test
  void testOutlineWithSmallPartStaysVisible() throws GeometryException {
    var outline = new VectorTile.Feature(
      Building.LAYER_NAME,
      1,
      VectorTile.encodeGeometry(rectangle(10, 20)),
      new HashMap<>(Map.of("render_height", 30)),
      0
    );
    var part = new VectorTile.Feature(
      Building.LAYER_NAME,
      2,
      VectorTile.encodeGeometry(rectangle(12, 14)),
      new HashMap<>(Map.of("render_height", 40, "_part", true)),
      0
    );

    var result = profileHidingOutlinesWithParts.postProcessLayerFeatures(Building.LAYER_NAME, 14,
      List.of(outline, part));

    Assertions.assertEquals(List.of(
      Map.of("render_height", 30),
      Map.of("render_height", 40)
    ), result.stream().map(VectorTile.Feature::tags).toList());
  }

  @Test
  void testOutlineCoveredByPartsWhereDefaultIntersectionThrows() throws GeometryException, ParseException {
    // 90 Washington Street, New York (way 278033632) and its parts, as they are in tile 14/4823/6160
    var wkt = new WKTReader();
    var outline = new VectorTile.Feature(
      Building.LAYER_NAME,
      1,
      VectorTile.encodeGeometry(wkt.read(
        "POLYGON ((131.6875 89.8125, 129.8125 94.1875, 124.625 91.75, 126.625 87.4375, 131.6875 89.8125))")),
      new HashMap<>(Map.of("render_height", 30)),
      0
    );
    List<VectorTile.Feature> items = new ArrayList<>(List.of(outline));
    for (String part : List.of(
      "POLYGON ((125.25 92.0625, 126.875 88.5, 127.8125 88.875, 126.1875 92.5, 125.25 92.0625))",
      "POLYGON ((127.4375 89.75, 129.6875 90.8125, 129.25 91.75, 127 90.6875, 127.4375 89.75))",
      "POLYGON ((129.8125 94.1875, 128.9375 93.8125, 130.625 90.1875, 126.875 88.5, 125.25 92.0625, 124.625 91.75, 126.625 87.4375, 131.6875 89.8125, 129.8125 94.1875))",
      "POLYGON ((127.125 92.9375, 126.1875 92.5, 127.8125 88.875, 130.625 90.1875, 129.875 91.875, 129.4375 91.6875, 129.125 92.4375, 127.6875 91.8125, 127.125 92.9375))",
      "POLYGON ((129.875 91.875, 128.9375 93.8125, 127.125 92.9375, 127.6875 91.8125, 129.125 92.4375, 129.4375 91.6875, 129.875 91.875))",
      "POLYGON ((125.625 92.25, 125.4375 92.1875, 125.0625 92.4375, 124.5625 93.375, 124.25 94.1875, 123.5625 93.875, 124.25 91.625, 125.625 92.25))",
      "POLYGON ((128.1875 96.0625, 127.875 96.375, 127.6875 96.5, 127.1875 96.4375, 125.375 95.625, 124.25 94.9375, 124.1875 94.75, 124.25 94.1875, 123.5625 93.875, 124.25 91.625, 129.8125 94.1875, 129.125 96.4375, 128.1875 96.0625))",
      "POLYGON ((126.1875 92.5, 128.9375 93.8125, 129.0625 94, 129.125 94.375, 129 94.6875, 127.875 96.375, 127.5 96.5, 126.3125 96.125, 124.25 94.9375, 124.1875 94.75, 124.25 94.1875, 124.8125 92.875, 125.1875 92.3125, 125.625 92.25, 126.1875 92.5))",
      "POLYGON ((128.9375 93.8125, 129.8125 94.1875, 129.125 96.4375, 128.1875 96.0625, 129.125 94.375, 129.0625 94, 128.9375 93.8125))"
    )) {
      items.add(new VectorTile.Feature(
        Building.LAYER_NAME,
        2,
        VectorTile.encodeGeometry(wkt.read(part)),
        new HashMap<>(Map.of("render_height", 20, "_part", true)),
        0
      ));
    }

    var result = profileHidingOutlinesWithParts.postProcessLayerFeatures(Building.LAYER_NAME, 14, items);

    Assertions.assertEquals(
      Map.of("render_height", 30, "hide_3d", true),
      result.stream().filter(feature -> feature.id() == 1).findFirst().orElseThrow().tags()
    );
  }

  @Test
  void testMergePolygonsZ13() throws GeometryException {
    var poly1 = new VectorTile.Feature(
      Building.LAYER_NAME,
      1,
      VectorTile.encodeGeometry(rectangle(10, 20)),
      Map.of(),
      0
    );
    var poly2 = new VectorTile.Feature(
      Building.LAYER_NAME,
      1,
      VectorTile.encodeGeometry(rectangle(20, 10, 22, 20)),
      Map.of(),
      0
    );

    Assertions.assertEquals(
      1, // merged into 1 multipolygon
      profile.postProcessLayerFeatures(Building.LAYER_NAME, 14, List.of(poly1, poly2)).size()
    );

    Assertions.assertEquals(
      2, // merged into 1 multipolygon
      profile.postProcessLayerFeatures(Building.LAYER_NAME, 14, List.of(poly1, poly2)).get(0).geometry().decode()
        .getNumGeometries()
    );
    Assertions.assertEquals(
      1,
      profile.postProcessLayerFeatures(Building.LAYER_NAME, 13, List.of(poly1, poly2)).size()
    );
    Assertions.assertEquals(
      1,
      profile.postProcessLayerFeatures(Building.LAYER_NAME, 13, List.of(poly1, poly2)).get(0).geometry().decode()
        .getNumGeometries()
    );
  }

  @Test
  void testColor() {
    assertFeatures(14, List.of(Map.of(
      "colour", "#ff0000"
    )), process(polygonFeature(Map.of(
      "building", "yes",
      "building:colour", "#ff0000",
      "building:material", "brick"
    ))));
    assertFeatures(14, List.of(Map.of(
      "colour", "#bd8161"
    )), process(polygonFeature(Map.of(
      "building", "yes",
      "building:building", "yes",
      "building:material", "brick"
    ))));
    assertFeatures(13, List.of(Map.of(
      "colour", "<null>"
    )), process(polygonFeature(Map.of(
      "building", "yes",
      "building:building", "yes",
      "building:colour", "#ff0000"
    ))));
  }
}
