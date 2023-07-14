package org.openmaptiles.layers;

import static com.onthegomap.planetiler.TestUtils.newLineString;
import static com.onthegomap.planetiler.TestUtils.rectangle;

import com.onthegomap.planetiler.TestUtils;
import com.onthegomap.planetiler.geo.GeoUtils;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SimpleFeature;
import com.onthegomap.planetiler.reader.SourceFeature;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Geometry;
import org.openmaptiles.OpenMapTilesProfile;

class WaterNameTest extends AbstractLayerTest {

  @Test
  void testWaterNamePoint() {
    assertFeatures(11, List.of(Map.of(
      "_layer", "water"
    ), Map.of(
      "class", "lake",
      "name", "waterway",
      "name:es", "waterway es",
      "intermittent", 1,

      "_layer", "water_name",
      "_type", "point",
      "_minzoom", 3,
      "_maxzoom", 14
    )), process(polygonFeatureWithArea(1, Map.of(
      "name", "waterway",
      "name:es", "waterway es",
      "natural", "water",
      "water", "pond",
      "intermittent", "1"
    ))));
    // 1/4 th of tile area is the threshold, 1/4 = 0.25 => area->side:0.25->0.5 => slightly bigger -> 0.51
    double z11area = Math.pow(0.51d / Math.pow(2, 11), 2);
    assertFeatures(10, List.of(Map.of(
      "_layer", "water"
    ), Map.of(
      "_layer", "water_name",
      "_type", "point",
      "_minzoom", 11,
      "_maxzoom", 14
    )), process(polygonFeatureWithArea(z11area, Map.of(
      "name", "waterway",
      "natural", "water",
      "water", "pond"
    ))));
  }

  // https://zelonewolf.github.io/openstreetmap-americana/#map=13/41.43989/-71.5716
  @Test
  void testWordenPondNamePoint() {
    assertFeatures(10, List.of(Map.of(
      "_layer", "water"
    ), Map.of(
      "_layer", "water_name",
      "_type", "point",
      "_minzoom", 13,
      "_maxzoom", 14
    )), process(polygonFeatureWithArea(4.930387948170328E-9, Map.of(
      "name", "waterway",
      "natural", "water",
      "water", "pond"
    ))));
  }

  @Test
  void testWaterNameLakeline() {
    assertFeatures(11, List.of(), process(SimpleFeature.create(
      newLineString(0, 0, 1, 1),
      new HashMap<>(Map.<String, Object>of(
        "OSM_ID", -10
      )),
      OpenMapTilesProfile.LAKE_CENTERLINE_SOURCE,
      null,
      0
    )));
    assertFeatures(10, List.of(Map.of(
      "_layer", "water"
    ), Map.of(
      "name", "waterway",
      "name:es", "waterway es",

      "_layer", "water_name",
      "_type", "line",
      "_geom", new TestUtils.NormGeometry(GeoUtils.latLonToWorldCoords(newLineString(0, 0, 1, 1))),
      "_minzoom", 3,
      "_maxzoom", 14,
      "_minpixelsize", "waterway".length() * 6d
    )), process(SimpleFeature.create(
      GeoUtils.worldToLatLonCoords(rectangle(0, Math.sqrt(1))),
      new HashMap<>(Map.<String, Object>of(
        "name", "waterway",
        "name:es", "waterway es",
        "natural", "water",
        "water", "pond"
      )),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      10
    )));
  }

  @Test
  void testWaterNameMultipleLakelines() {
    assertFeatures(11, List.of(), process(SimpleFeature.create(
      newLineString(0, 0, 1, 1),
      new HashMap<>(Map.<String, Object>of(
        "OSM_ID", -10
      )),
      OpenMapTilesProfile.LAKE_CENTERLINE_SOURCE,
      null,
      0
    )));
    assertFeatures(11, List.of(), process(SimpleFeature.create(
      newLineString(2, 2, 3, 3),
      new HashMap<>(Map.<String, Object>of(
        "OSM_ID", -10
      )),
      OpenMapTilesProfile.LAKE_CENTERLINE_SOURCE,
      null,
      0
    )));
    assertFeatures(10, List.of(Map.of(
      "_layer", "water"
    ), Map.of(
      "name", "waterway",
      "name:es", "waterway es",

      "_layer", "water_name",
      "_geom",
      new TestUtils.NormGeometry(
        GeoUtils.latLonToWorldCoords(GeoUtils.JTS_FACTORY.createGeometryCollection(new Geometry[]{
          newLineString(0, 0, 1, 1),
          newLineString(2, 2, 3, 3)
        }))),
      "_minzoom", 3,
      "_maxzoom", 14,
      "_minpixelsize", "waterway".length() * 6d
    )), process(SimpleFeature.create(
      GeoUtils.worldToLatLonCoords(rectangle(0, Math.sqrt(1))),
      new HashMap<>(Map.<String, Object>of(
        "name", "waterway",
        "name:es", "waterway es",
        "natural", "water",
        "water", "pond"
      )),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      10
    )));
  }

  @Test
  void testWaterNameBay() {
    assertFeatures(11, List.of(), process(SimpleFeature.create(
      newLineString(0, 0, 1, 1),
      new HashMap<>(Map.<String, Object>of(
        "OSM_ID", -10
      )),
      OpenMapTilesProfile.LAKE_CENTERLINE_SOURCE,
      null,
      0
    )));
    assertFeatures(10, List.of(Map.of(
      "name", "bay",
      "name:es", "bay es",

      "_layer", "water_name",
      "_type", "line",
      "_geom", new TestUtils.NormGeometry(GeoUtils.latLonToWorldCoords(newLineString(0, 0, 1, 1))),
      "_minzoom", 9,
      "_maxzoom", 14,
      "_minpixelsize", "bay".length() * 6d
    )), process(SimpleFeature.create(
      GeoUtils.worldToLatLonCoords(rectangle(0, Math.sqrt(1))),
      new HashMap<>(Map.<String, Object>of(
        "name", "bay",
        "name:es", "bay es",
        "natural", "bay"
      )),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      10
    )));
  }

  @Test
  void testMarinePoint() {
    assertFeatures(11, List.of(), process(SimpleFeature.create(
      newLineString(0, 0, 1, 1),
      new HashMap<>(Map.<String, Object>of(
        "scalerank", 1,
        "name", "Black sea"
      )),
      OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
      "ne_10m_geography_marine_polys",
      0
    )));

    // name match - use scale rank from NE
    assertFeatures(10, List.of(Map.of(
      "name", "Black Sea",
      "name:es", "Mar Negro",
      "_layer", "water_name",
      "_type", "point",
      "_minzoom", 1,
      "_maxzoom", 14
    )), process(pointFeature(Map.of(
      "rank", 9,
      "name", "Black Sea",
      "name:es", "Mar Negro",
      "place", "sea"
    ))));

    // name match but ocean - use min zoom=0
    assertFeatures(10, List.of(Map.of(
      "_layer", "water_name",
      "_type", "point",
      "_minzoom", 0,
      "_maxzoom", 14
    )), process(pointFeature(Map.of(
      "rank", 9,
      "name", "Black Sea",
      "place", "ocean"
    ))));

    // no name match - use OSM rank
    assertFeatures(10, List.of(Map.of(
      "_layer", "water_name",
      "_type", "point",
      "_minzoom", 9,
      "_maxzoom", 14
    )), process(pointFeature(Map.of(
      "rank", 9,
      "name", "Atlantic",
      "place", "sea"
    ))));

    // no rank at all, default to 8
    assertFeatures(10, List.of(Map.of(
      "_layer", "water_name",
      "_type", "point",
      "_minzoom", 8,
      "_maxzoom", 14
    )), process(pointFeature(Map.of(
      "name", "Atlantic",
      "place", "sea"
    ))));
  }

  private record TestEntry(
    SourceFeature feature,
    int expectedZoom
  ) {}

  private void createAreaForMinZoomTest(List<TestEntry> testEntries, double side, int expectedZoom, String name) {
    double area = Math.pow(side, 2);
    var feature = polygonFeatureWithArea(area, Map.of(
      "name", name,
      "natural", "water"
    ));
    testEntries.add(new TestEntry(
      feature,
      Math.min(14, Math.max(3, expectedZoom))
    ));
  }

  @Test
  void testAreaToMinZoom() throws GeometryException {
    // threshold is 1/4 of tile area, hence ...
    // ... side if 1/2 tile side: from pixels to world coord, for say Z14 ...
    //final double HALF_OF_TILE_SIDE = 128d / Math.pow(2d, 14d + 8d);
    // ... and then for some lower zoom:
    //double testAreaSide = HALF_OF_TILE_SIDE * Math.pow(2, 14 - zoom);
    // all this then simplified to `testAreaSide` calculation bellow

    final List<TestEntry> testEntries = new ArrayList<>();
    for (int zoom = 14; zoom >= 0; zoom--) {
      double testAreaSide = Math.pow(2, -zoom - 1);

      // slightly bellow the threshold
      createAreaForMinZoomTest(testEntries, testAreaSide * 0.999, zoom + 1, "waterway-");
      // precisely at the threshold
      createAreaForMinZoomTest(testEntries, testAreaSide, zoom, "waterway=");
      // slightly over the threshold
      createAreaForMinZoomTest(testEntries, testAreaSide * 1.001, zoom, "waterway+");
    }

    for (var entry : testEntries) {
      assertFeatures(10, List.of(Map.of(
        "_layer", "water"
      ), Map.of(
        "_layer", "water_name",
        "_type", "point",
        "_minzoom", entry.expectedZoom,
        "_maxzoom", 14
      )), process(entry.feature));
    }
  }
}
