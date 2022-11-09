package org.openmaptiles.layers;

import static com.onthegomap.planetiler.TestUtils.newPoint;
import static com.onthegomap.planetiler.TestUtils.rectangle;

import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.config.PlanetilerConfig;
import com.onthegomap.planetiler.geo.GeoUtils;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SimpleFeature;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmReader;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openmaptiles.OpenMapTilesProfile;
import org.openmaptiles.generated.Tables;

class BuildingTest extends AbstractLayerTest {

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
      2,
      profile.postProcessLayerFeatures(Building.LAYER_NAME, 14, List.of(poly1, poly2)).size()
    );
    Assertions.assertEquals(
      1,
      profile.postProcessLayerFeatures(Building.LAYER_NAME, 13, List.of(poly1, poly2)).size()
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

  @Disabled
  @Test
  void coalesceFInBuildingProcessBenchmark() {
    /* The point is to hit Building.process hard (hence @Disabled annotation), measure time and see whether transition
     * from `coalesce()` to `coalesceF()` helps tpo achieve shorter time. In theory, it should since half of calls of
     * `parseDoubleOrNull()` should be avoided if firs value (say `height`) is not null thus lambda for second value
     * (`parseDoubleOrNull` for say `buildingheight`) is not called.
     *
     * Values for this UT:
     * a) before (with `coalesce()`): 3.690s, 3.638s, 3.577s
     * b) after (with `coalesceF()`:  2.564s, 2.436s, 2.458s
     *
     * Values for Slovakia tiles generation:
     * a) before: Finished in 5m40s cpu:28m38s gc:10s avg:5.1; ... 5m38s cpu:28m52s gc:9s avg:5.1; ... 5m30s cpu:28m16s gc:9s avg:5.1
     * b) after:  Finished in 4m53s cpu:28m3s gc:8s avg:5.7;   ... 4m45s cpu:27m11s gc:8s avg:5.7; ... 4m44s cpu:27m8s gc:8s avg:5.7
     */
    PlanetilerConfig config = PlanetilerConfig.defaults();
    Building building = new Building(null, config, null);
    var sourceFeature = SimpleFeature.create(newPoint(0, 0), Map.of("abc", "123"), "source", "source_layer", 99);
    Tables.OsmBuildingPolygon element = new Tables.OsmBuildingPolygon(
      null, null, null,
      null, "3661", "3662",
      null, null, "3663",
      "3664", "3665", "3666", sourceFeature
    );

    for (long i = 0; i < 10_000_000; i++) {
      building.process(element, null);
    }
  }
}
