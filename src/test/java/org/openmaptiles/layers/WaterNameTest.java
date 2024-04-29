package org.openmaptiles.layers;

import static com.onthegomap.planetiler.TestUtils.newLineString;
import static com.onthegomap.planetiler.TestUtils.rectangle;

import com.onthegomap.planetiler.TestUtils;
import com.onthegomap.planetiler.geo.GeoUtils;
import com.onthegomap.planetiler.reader.SimpleFeature;
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
      GeoUtils.worldToLatLonCoords(rectangle(0, Math.sqrt(1E-7))),
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
      GeoUtils.worldToLatLonCoords(rectangle(0, Math.sqrt(1E-7))),
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
  void testWaterNameBaySmall() {
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
    ), Map.of(
      "name", "bay",
      "name:es", "bay es",

      "_layer", "water_name",
      "_type", "point",
      "_minzoom", 3,
      "_maxzoom", 8,
      "_minpixelsize", 128d
    )), process(SimpleFeature.create(
      GeoUtils.worldToLatLonCoords(rectangle(0, Math.sqrt(1E-7))),
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
  void testWaterNameBayBig() {
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
      "_minzoom", 9,
      "_maxzoom", 14
    ), Map.of(
      "name", "bay",
      "name:es", "bay es",

      "_layer", "water_name",
      "_type", "point",
      "_minzoom", 3,
      "_maxzoom", 8
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
}
