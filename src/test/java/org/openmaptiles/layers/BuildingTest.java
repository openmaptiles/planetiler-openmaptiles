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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
