package org.openmaptiles.layers;

import static com.onthegomap.planetiler.TestUtils.rectangle;

import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.geo.GeoUtils;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SimpleFeature;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmReader;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openmaptiles.OpenMapTilesProfile;

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
