package org.openmaptiles.layers;

import static com.onthegomap.planetiler.TestUtils.rectangle;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.geo.GeoUtils;
import com.onthegomap.planetiler.reader.SimpleFeature;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openmaptiles.OpenMapTilesProfile;

class WaterTest extends AbstractLayerTest {


  @Test
  void testWaterNaturalEarth() {
    assertFeatures(0, List.of(Map.of(
      "class", "ocean",
      "intermittent", "<null>",
      "_layer", "water",
      "_type", "polygon",
      "_minzoom", 0
    )), process(SimpleFeature.create(
      rectangle(0, 10),
      Map.of(),
      OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
      "ne_110m_ocean",
      0
    )));

    assertFeatures(6, List.of(Map.of(
      "class", "ocean",
      "_layer", "water",
      "_type", "polygon",
      "_maxzoom", 5
    )), process(SimpleFeature.create(
      rectangle(0, 10),
      Map.of(),
      OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
      "ne_10m_ocean",
      0
    )));
  }

  @Test
  void testLakeNaturalEarthByIntersection() {
    final var polygon = rectangle(0, 0.1);
    // NE lakes:
    process(SimpleFeature.create(
      polygon,
      Map.of(),
      OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
      "ne_110m_lakes",
      0
    ));
    process(SimpleFeature.create(
      polygon,
      Map.of(),
      OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
      "ne_10m_lakes",
      0
    ));
    // OSM lake to take the ID from:
    process(SimpleFeature.create(
      polygon,
      new HashMap<>(Map.<String, Object>of(
        "natural", "water",
        "water", "reservoir"
      )),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      123
    ));

    List<FeatureCollector.Feature> features = new ArrayList<>();
    profile.finish(OpenMapTilesProfile.OSM_SOURCE, new FeatureCollector.Factory(params, stats), features::add);
    assertFeatures(0, List.of(Map.of(
      "class", "lake",
      "intermittent", "<null>",
      "id", 123L,
      "_layer", "water",
      "_type", "polygon",
      "_minzoom", 0,
      "_maxzoom", 1
    ), Map.of(
      "class", "lake",
      "intermittent", "<null>",
      "id", 123L,
      "_layer", "water",
      "_type", "polygon",
      "_minzoom", 4,
      "_maxzoom", 5
    )), features);
  }

  @Test
  void testLakeNaturalEarthIntersectionMiss() {
    final var polygon1 = rectangle(0, 0.1);
    final var polygon2 = rectangle(0.2, 0.3);
    // NE lakes:
    process(SimpleFeature.create(
      polygon1,
      Map.of(),
      OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
      "ne_110m_lakes",
      0
    ));
    process(SimpleFeature.create(
      polygon1,
      Map.of(),
      OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
      "ne_10m_lakes",
      0
    ));
    // OSM lake to take the ID from:
    process(SimpleFeature.create(
      polygon2,
      new HashMap<>(Map.<String, Object>of(
        "natural", "water",
        "water", "reservoir"
      )),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      123
    ));

    List<FeatureCollector.Feature> features = new ArrayList<>();
    profile.finish(OpenMapTilesProfile.OSM_SOURCE, new FeatureCollector.Factory(params, stats), features::add);
    assertFeatures(0, List.of(Map.of(
      "class", "lake",
      "id", "<null>",
      "_layer", "water",
      "_type", "polygon"
    ), Map.of(
      "class", "lake",
      "id", "<null>",
      "_layer", "water",
      "_type", "polygon"
    )), features);
  }

  @Test
  void testLakeNaturalEarthByBiggerIntersection() {
    final var polygon1 = rectangle(0, 0.1);
    final var polygon2 = rectangle(0, 0.2);
    // NE lakes:
    process(SimpleFeature.create(
      polygon2,
      Map.of(),
      OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
      "ne_110m_lakes",
      0
    ));
    process(SimpleFeature.create(
      polygon2,
      Map.of(),
      OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
      "ne_10m_lakes",
      0
    ));
    // OSM lakes to take the ID from:
    process(SimpleFeature.create(
      polygon1,
      new HashMap<>(Map.<String, Object>of(
        "natural", "water",
        "water", "reservoir"
      )),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      123
    ));
    process(SimpleFeature.create(
      polygon2,
      new HashMap<>(Map.<String, Object>of(
        "natural", "water",
        "water", "reservoir"
      )),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      234
    ));

    List<FeatureCollector.Feature> features = new ArrayList<>();
    profile.finish(OpenMapTilesProfile.OSM_SOURCE, new FeatureCollector.Factory(params, stats), features::add);
    assertFeatures(0, List.of(Map.of(
      "class", "lake",
      "intermittent", "<null>",
      "id", 234L,
      "_layer", "water",
      "_type", "polygon",
      "_minzoom", 0,
      "_maxzoom", 1
    ), Map.of(
      "class", "lake",
      "intermittent", "<null>",
      "id", 234L,
      "_layer", "water",
      "_type", "polygon",
      "_minzoom", 4,
      "_maxzoom", 5
    )), features);
  }

  @Test
  void testLakeNaturalEarthByName() {
    final var polygon = rectangle(0, 0.1);
    // NE lakes:
    process(SimpleFeature.create(
      polygon,
      Map.of("name", "Test Lake"),
      OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
      "ne_50m_lakes",
      0
    ));
    process(SimpleFeature.create(
      polygon,
      Map.of("name", "Test Lake"),
      OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
      "ne_10m_lakes",
      0
    ));
    // OSM lake to take the ID from:
    process(SimpleFeature.create(
      polygon,
      new HashMap<>(Map.<String, Object>of(
        "name", "Test Lake",
        "natural", "water",
        "water", "reservoir"
      )),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      123
    ));

    List<FeatureCollector.Feature> features = new ArrayList<>();
    profile.finish(OpenMapTilesProfile.OSM_SOURCE, new FeatureCollector.Factory(params, stats), features::add);
    assertFeatures(0, List.of(Map.of(
      "class", "lake",
      "id", 123L,
      "_layer", "water",
      "_minzoom", 2,
      "_maxzoom", 3
    ), Map.of(
      "class", "lake",
      "id", 123L,
      "_layer", "water",
      "_minzoom", 4,
      "_maxzoom", 5
    )), features);
  }

  @Test
  void testLakeNaturalEarthByNameIntersectionMiss() {
    final var polygon1 = rectangle(0, 0.1);
    final var polygon2 = rectangle(0.2, 0.3);
    // NE lake:
    process(SimpleFeature.create(
      polygon1,
      Map.of("name", "Test Lake"),
      OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
      "ne_50m_lakes",
      0
    ));
    // OSM lake to take the ID from:
    process(SimpleFeature.create(
      polygon2,
      new HashMap<>(Map.<String, Object>of(
        "name", "Test Lake",
        "natural", "water",
        "water", "reservoir"
      )),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      123
    ));

    List<FeatureCollector.Feature> features = new ArrayList<>();
    profile.finish(OpenMapTilesProfile.OSM_SOURCE, new FeatureCollector.Factory(params, stats), features::add);
    assertFeatures(0, List.of(Map.of(
      "class", "lake",
      "id", "<null>",
      "_layer", "water"
    )), features);
  }

  @Test
  void testLakeNaturalEarthByNameAndBiggerIntersection() {
    final var polygon1 = rectangle(0, 0.1);
    final var polygon2 = rectangle(0, 0.2);
    // NE lake:
    process(SimpleFeature.create(
      polygon2,
      Map.of("name", "Test Lake"),
      OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
      "ne_50m_lakes",
      0
    ));
    // OSM lakes to take the ID from:
    process(SimpleFeature.create(
      polygon1,
      new HashMap<>(Map.<String, Object>of(
        "name", "Test Lake",
        "natural", "water",
        "water", "reservoir"
      )),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      123
    ));
    process(SimpleFeature.create(
      polygon2,
      new HashMap<>(Map.<String, Object>of(
        "name", "Test Lake",
        "natural", "water",
        "water", "reservoir"
      )),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      234
    ));

    List<FeatureCollector.Feature> features = new ArrayList<>();
    profile.finish(OpenMapTilesProfile.OSM_SOURCE, new FeatureCollector.Factory(params, stats), features::add);
    assertFeatures(0, List.of(Map.of(
      "class", "lake",
      "id", 234L,
      "_layer", "water"
    )), features);
  }

  @Test
  void testLakeNaturalEarthByNameWithColision() {
    final var polygonSmaller = rectangle(0, 0.1);
    final var polygonBigger = rectangle(0, 0.2);
    // NE lakes:
    process(SimpleFeature.create(
      polygonSmaller,
      Map.of("name", "Test Lake"),
      OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
      "ne_10m_lakes",
      0
    ));
    process(SimpleFeature.create(
      polygonBigger,
      Map.of("name", "Test Lake"),
      OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
      "ne_10m_lakes",
      0
    ));
    // OSM lake to take the ID from:
    process(SimpleFeature.create(
      polygonBigger,
      new HashMap<>(Map.<String, Object>of(
        "name", "Test Lake",
        "natural", "water",
        "water", "reservoir"
      )),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      123
    ));

    List<FeatureCollector.Feature> features = new ArrayList<>();
    profile.finish(OpenMapTilesProfile.OSM_SOURCE, new FeatureCollector.Factory(params, stats), features::add);
    assertFeatures(0, List.of(Map.of(
      "class", "lake",
      "id", "<null>",
      "_layer", "water"
    ), Map.of(
      "class", "lake",
      "id", 123L,
      "_layer", "water"
    )), features);
  }

  @Test
  void testWaterOsmWaterPolygon() {
    assertFeatures(0, List.of(Map.of(
      "class", "ocean",
      "intermittent", "<null>",
      "_layer", "water",
      "_type", "polygon",
      "_minzoom", 6,
      "_maxzoom", 14
    )), process(SimpleFeature.create(
      rectangle(0, 10),
      Map.of(),
      OpenMapTilesProfile.WATER_POLYGON_SOURCE,
      null,
      0
    )));
  }

  @Test
  void testWaterOsmId() {
    long id = 123;
    assertFeatures(14, List.of(Map.of(
      "class", "lake",
      "id", id,
      "_layer", "water",
      "_type", "polygon",
      "_minzoom", 6,
      "_maxzoom", 14
    )), process(SimpleFeature.create(
      GeoUtils.worldToLatLonCoords(rectangle(0, Math.sqrt(1))),
      new HashMap<>(Map.<String, Object>of(
        "natural", "water",
        "water", "reservoir"
      )),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      id
    )));
  }

  @Test
  void testWater() {
    assertFeatures(14, List.of(Map.of(
      "class", "lake",
      "_layer", "water",
      "_type", "polygon",
      "_minzoom", 6,
      "_maxzoom", 14
    )), process(polygonFeature(Map.of(
      "natural", "water",
      "water", "reservoir"
    ))));
    assertFeatures(14, List.of(
      Map.of("_layer", "poi"),
      Map.of(
        "class", "swimming_pool",

        "_layer", "water",
        "_type", "polygon",
        "_minzoom", 6,
        "_maxzoom", 14
      )), process(polygonFeature(Map.of(
        "leisure", "swimming_pool"
      ))));
    assertFeatures(14, List.of(), process(polygonFeature(Map.of(
      "natural", "bay"
    ))));
    assertFeatures(14, List.of(Map.of()), process(polygonFeature(Map.of(
      "natural", "water"
    ))));
    assertFeatures(14, List.of(), process(polygonFeature(Map.of(
      "natural", "water",
      "covered", "yes"
    ))));
    assertFeatures(14, List.of(
      Map.of(
        "class", "dock",
        "intermittent", 1,

        "_layer", "water",
        "_type", "polygon",
        "_minzoom", 6,
        "_maxzoom", 14),
      Map.of(
        "class", "harbor",
        "subclass", "dock",

        "_layer", "poi",
        "_type", "point",
        "_minzoom", 14,
        "_maxzoom", 14
      )), process(polygonFeature(Map.of(
        "waterway", "dock",
        "intermittent", "1"
      ))));
    assertFeatures(11, List.of(Map.of(
      "class", "lake",
      "brunnel", "<null>",
      "intermittent", 0,

      "_layer", "water",
      "_type", "polygon",
      "_minzoom", 6,
      "_maxzoom", 14,
      "_minpixelsize", 2d
    )), process(polygonFeature(Map.of(
      "landuse", "salt_pond",
      "bridge", "1"
    ))));
  }

  @Test
  void testDock() {
    assertFeatures(11, List.of(
      Map.of(
        "class", "dock",
        "_layer", "water",
        "_type", "polygon"),
      Map.of(
        "class", "harbor",
        "_layer", "poi",
        "_type", "point"
      )), process(polygonFeature(Map.of(
        "waterway", "dock"
      ))));
  }

  @Test
  void testRiver() {
    assertFeatures(11, List.of(Map.of(
      "class", "river",
      "_layer", "water",
      "_type", "polygon"
    )), process(polygonFeature(Map.of(
      "water", "river"
    ))));
  }

  @Test
  void testSpring() {
    assertFeatures(11, List.of(Map.of(
      "class", "lake",
      "_layer", "water",
      "_type", "polygon"
    )), process(polygonFeature(Map.of(
      "natural", "spring"
    ))));
  }

  @Test
  void testOceanZoomLevels() {
    assertCoversZoomRange(0, 14, "water",
      process(SimpleFeature.create(
        rectangle(0, 10),
        Map.of(),
        OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
        "ne_110m_ocean",
        0
      )),
      process(SimpleFeature.create(
        rectangle(0, 10),
        Map.of(),
        OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
        "ne_50m_ocean",
        0
      )),
      process(SimpleFeature.create(
        rectangle(0, 10),
        Map.of(),
        OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
        "ne_10m_ocean",
        0
      )),
      process(SimpleFeature.create(
        rectangle(0, 10),
        Map.of(),
        OpenMapTilesProfile.WATER_POLYGON_SOURCE,
        null,
        0
      ))
    );
  }

  @Test
  void testLakeZoomLevels() {
    assertCoversZoomRange(6, 14, "water",
      process(SimpleFeature.create(
        rectangle(0, 10),
        Map.of(
          "natural", "water",
          "water", "reservoir"
        ),
        OpenMapTilesProfile.OSM_SOURCE,
        null,
        0
      ))
    );
  }
}
