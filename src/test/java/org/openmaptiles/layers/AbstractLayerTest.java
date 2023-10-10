package org.openmaptiles.layers;

import static com.onthegomap.planetiler.TestUtils.assertSubmap;
import static com.onthegomap.planetiler.TestUtils.newLineString;
import static com.onthegomap.planetiler.TestUtils.newPoint;
import static com.onthegomap.planetiler.TestUtils.rectangle;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.TestUtils;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.config.PlanetilerConfig;
import com.onthegomap.planetiler.geo.GeoUtils;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SimpleFeature;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.reader.osm.OsmReader;
import com.onthegomap.planetiler.reader.osm.OsmRelationInfo;
import com.onthegomap.planetiler.stats.Stats;
import com.onthegomap.planetiler.util.Translations;
import com.onthegomap.planetiler.util.Wikidata;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.openmaptiles.OpenMapTilesProfile;
import org.openmaptiles.util.Utils;

public abstract class AbstractLayerTest {

  final Wikidata.WikidataTranslations wikidataTranslations = new Wikidata.WikidataTranslations();
  final Translations translations = Translations.defaultProvider(List.of("en", "es", "de"))
    .addFallbackTranslationProvider(wikidataTranslations);

  final PlanetilerConfig params = PlanetilerConfig.defaults();
  final OpenMapTilesProfile profile = new OpenMapTilesProfile(translations, PlanetilerConfig.defaults(),
    Stats.inMemory());
  final Stats stats = Stats.inMemory();
  final FeatureCollector.Factory featureCollectorFactory = new FeatureCollector.Factory(params, stats);

  static void assertFeatures(int zoom, List<Map<String, Object>> expected, Iterable<FeatureCollector.Feature> actual) {
    // ensure both are sorted by layer
    var expectedList =
      expected.stream().sorted(Comparator.comparing(d -> Utils.coalesce(d.get("_layer"), "").toString())).toList();
    var actualList = StreamSupport.stream(actual.spliterator(), false)
      .sorted(Comparator.comparing(FeatureCollector.Feature::getLayer))
      .toList();
    assertEquals(expectedList.size(), actualList.size(), () -> "size: " + actualList);
    for (int i = 0; i < expectedList.size(); i++) {
      assertSubmap(expectedList.get(i), TestUtils.toMap(actualList.get(i), zoom));
    }
  }

  static void assertDescending(int... vals) {
    for (int i = 1; i < vals.length; i++) {
      if (vals[i - 1] < vals[i]) {
        fail("element at " + (i - 1) + " is less than element at " + i);
      }
    }
  }

  static void assertAscending(int... vals) {
    for (int i = 1; i < vals.length; i++) {
      if (vals[i - 1] > vals[i]) {
        fail(
          Arrays.toString(vals) +
            System.lineSeparator() + "element at " + (i - 1) + " (" + vals[i - 1] + ") is greater than element at " +
            i + " (" + vals[i] + ")");
      }
    }
  }

  VectorTile.Feature pointFeature(String layer, Map<String, Object> map, int group) {
    return new VectorTile.Feature(
      layer,
      1,
      VectorTile.encodeGeometry(newPoint(0, 0)),
      new HashMap<>(map),
      group
    );
  }

  FeatureCollector process(SourceFeature feature) {
    var collector = featureCollectorFactory.get(feature);
    profile.processFeature(feature, collector);
    return collector;
  }

  void assertCoversZoomRange(int minzoom, int maxzoom, String layer, FeatureCollector... featureCollectors) {
    Map<?, ?>[] zooms = new Map[Math.max(15, maxzoom + 1)];
    for (var features : featureCollectors) {
      for (var feature : features) {
        if (feature.getLayer().equals(layer)) {
          for (int zoom = feature.getMinZoom(); zoom <= feature.getMaxZoom(); zoom++) {
            Map<String, Object> map = TestUtils.toMap(feature, zoom);
            if (zooms[zoom] != null) {
              fail("Multiple features at z" + zoom + ":" + System.lineSeparator() + zooms[zoom] + "\n" + map);
            }
            zooms[zoom] = map;
          }
        }
      }
    }
    for (int zoom = 0; zoom <= 14; zoom++) {
      if (zoom < minzoom || zoom > maxzoom) {
        if (zooms[zoom] != null) {
          fail("Expected nothing at z" + zoom + " but found: " + zooms[zoom]);
        }
      } else {
        if (zooms[zoom] == null) {
          fail("No feature at z" + zoom);
        }
      }
    }
  }

  SourceFeature pointFeature(Map<String, Object> props) {
    return SimpleFeature.create(
      newPoint(0, 0),
      new HashMap<>(props),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      0
    );
  }

  SourceFeature lineFeature(Map<String, Object> props) {
    return SimpleFeature.create(
      newLineString(0, 0, 1, 1),
      new HashMap<>(props),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      0
    );
  }

  SourceFeature lineFeatureWithLength(double length, Map<String, Object> props) {
    return SimpleFeature.create(
      GeoUtils.worldToLatLonCoords(newLineString(0, 0, 0, length)),
      new HashMap<>(props),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      0
    );
  }

  SourceFeature closedWayFeature(Map<String, Object> props) {
    return SimpleFeature.createFakeOsmFeature(
      newLineString(0, 0, 1, 0, 1, 1, 0, 1, 0, 0),
      new HashMap<>(props),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      0,
      null
    );
  }

  SourceFeature polygonFeatureWithArea(double area, Map<String, Object> props) {
    return SimpleFeature.create(
      GeoUtils.worldToLatLonCoords(rectangle(0, Math.sqrt(area))),
      new HashMap<>(props),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      0
    );
  }

  SourceFeature polygonFeature(Map<String, Object> props) {
    return polygonFeatureWithArea(1, props);
  }


  protected SimpleFeature lineFeatureWithRelation(List<OsmRelationInfo> relationInfos,
    Map<String, Object> map) {
    return SimpleFeature.createFakeOsmFeature(
      newLineString(0, 0, 1, 1),
      map,
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      0,
      (relationInfos == null ? List.<OsmRelationInfo>of() : relationInfos).stream()
        .map(r -> new OsmReader.RelationMember<>("", r)).toList()
    );
  }

  protected void testMergesLinestrings(Map<String, Object> attrs, String layer, double length, int zoom)
    throws GeometryException {
    var line1 = new VectorTile.Feature(
      layer,
      1,
      VectorTile.encodeGeometry(newLineString(0, 0, length / 2, 0)),
      new HashMap<>(attrs),
      0
    );
    var line2 = new VectorTile.Feature(
      layer,
      1,
      VectorTile.encodeGeometry(newLineString(length / 2, 0, length, 0)),
      new HashMap<>(attrs),
      0
    );
    var connected = new VectorTile.Feature(
      layer,
      1,
      VectorTile.encodeGeometry(newLineString(0, 0, length, 0)),
      attrs,
      0
    );

    assertEquals(
      List.of(connected),
      profile.postProcessLayerFeatures(layer, zoom, List.of(line1, line2))
    );
  }

  protected void testDoesNotMergeLinestrings(Map<String, Object> attrs, String layer, double length, int zoom)
    throws GeometryException {
    var line1 = new VectorTile.Feature(
      layer,
      1,
      VectorTile.encodeGeometry(newLineString(0, 0, length / 2, 0)),
      new HashMap<>(attrs),
      0
    );
    var line2 = new VectorTile.Feature(
      layer,
      1,
      VectorTile.encodeGeometry(newLineString(length / 2, 0, length, 0)),
      new HashMap<>(attrs),
      0
    );

    assertEquals(
      List.of(line1, line2),
      profile.postProcessLayerFeatures(layer, zoom, List.of(line1, line2))
    );
  }

  public static Map<String, Object> mapOf(Object... args) {
    assert args.length % 2 == 0;
    Map<String, Object> result = new HashMap<>();
    for (int i = 0; i < args.length; i += 2) {
      String key = args[i].toString();
      Object value = args[i + 1];
      result.put(key, value == null ? "<null>" : value);
    }
    return result;
  }
}
