package org.openmaptiles.layers;

import static com.onthegomap.planetiler.TestUtils.newPoint;
import static com.onthegomap.planetiler.TestUtils.rectangle;
import static com.onthegomap.planetiler.collection.FeatureGroup.SORT_KEY_MAX;
import static com.onthegomap.planetiler.collection.FeatureGroup.SORT_KEY_MIN;
import static org.junit.jupiter.api.Assertions.fail;

import com.onthegomap.planetiler.geo.GeoUtils;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SimpleFeature;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openmaptiles.OpenMapTilesProfile;

class PlaceTest extends AbstractLayerTest {

  @Test
  void testContinent() {
    wikidataTranslations.put(49, "es", "América del Norte y América Central");
    assertFeatures(0, List.of(Map.of(
      "_layer", "place",
      "class", "continent",
      "name", "North America",
      "name:en", "North America",
      "name:es", "América del Norte",
      "name:latin", "North America",
      "rank", 1,

      "_type", "point",
      "_minzoom", 0,
      "_maxzoom", 3
    )), process(pointFeature(Map.of(
      "place", "continent",
      "wikidata", "Q49",
      "name:es", "América del Norte",
      "name", "North America",
      "name:en", "North America"
    ))));
  }

  @Test
  void testCountry() {
    wikidataTranslations.put(30, "es", "Estados Unidos");
    process(SimpleFeature.create(
      rectangle(0, 0.25),
      Map.of(
        "name", "United States",
        "scalerank", 0,
        "labelrank", 2
      ),
      OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
      "ne_10m_admin_0_countries",
      0
    ));
    assertFeatures(0, List.of(Map.of(
      "_layer", "place",
      "class", "country",
      "name", "United States of America",
      "name_en", "United States of America",
      "name:es", "Estados Unidos de América",
      "name:latin", "United States of America",
      "iso_a2", "US",
      "rank", 6,

      "_type", "point",
      "_minzoom", 5
    )), process(SimpleFeature.create(
      newPoint(0.5, 0.5),
      Map.of(
        "place", "country",
        "wikidata", "Q30",
        "name:es", "Estados Unidos de América",
        "name", "United States of America",
        "name:en", "United States of America",
        "country_code_iso3166_1_alpha_2", "US"
      ),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      0
    )));
    assertFeatures(0, List.of(Map.of(
      "_layer", "place",
      "class", "country",
      "name", "United States of America",
      "name_en", "United States of America",
      "name:es", "Estados Unidos de América",
      "name:latin", "United States of America",
      "iso_a2", "US",
      "rank", 1,

      "_type", "point",
      "_minzoom", 0
    )), process(SimpleFeature.create(
      newPoint(0.1, 0.1),
      Map.of(
        "place", "country",
        "wikidata", "Q30",
        "name:es", "Estados Unidos de América",
        "name", "United States of America",
        "name:en", "United States of America",
        "country_code_iso3166_1_alpha_2", "US"
      ),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      0
    )));
  }

  @Test
  void testState() {
    wikidataTranslations.put(771, "es", "Massachusetts es");
    process(SimpleFeature.create(
      rectangle(0, 0.25),
      Map.of(
        "name", "Massachusetts",
        "scalerank", 0,
        "labelrank", 2,
        "datarank", 1
      ),
      OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
      "ne_10m_admin_1_states_provinces",
      0
    ));

    process(SimpleFeature.create(
      rectangle(0.4, 0.6),
      Map.of(
        "name", "Massachusetts - not important",
        "scalerank", 8,
        "labelrank", 8,
        "datarank", 1
      ),
      OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
      "ne_10m_admin_1_states_provinces",
      0
    ));

    // no match
    assertFeatures(0, List.of(), process(SimpleFeature.create(
      newPoint(1, 1),
      Map.of(
        "place", "state",
        "wikidata", "Q771",
        "name", "Massachusetts",
        "name:en", "Massachusetts"
      ),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      0
    )));

    // unimportant match
    assertFeatures(0, List.of(), process(SimpleFeature.create(
      newPoint(0.5, 0.5),
      Map.of(
        "place", "state",
        "wikidata", "Q771",
        "name", "Massachusetts",
        "name:en", "Massachusetts"
      ),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      0
    )));

    // important match
    assertFeatures(0, List.of(Map.of(
      "_layer", "place",
      "class", "state",
      "name", "Massachusetts",
      "name_en", "Massachusetts",
      "name:es", "Massachusetts es",
      "name:latin", "Massachusetts",
      "rank", 1,

      "_type", "point",
      "_minzoom", 2
    )), process(SimpleFeature.create(
      newPoint(0.1, 0.1),
      Map.of(
        "place", "state",
        "wikidata", "Q771",
        "name", "Massachusetts",
        "name:en", "Massachusetts"
      ),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      0
    )));
  }

  @Test
  void testProvince() {
    wikidataTranslations.put(95027, "es", "provincia de Lugo");
    process(SimpleFeature.create(
      rectangle(0, 0.25),
      Map.of(
        "name", "Nova Scotia",
        "scalerank", 3,
        "labelrank", 3,
        "datarank", 3
      ),
      OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
      "ne_10m_admin_1_states_provinces",
      0
    ));

    assertFeatures(4, List.of(Map.of(
      "_layer", "place",
      "class", "province",
      "name", "Lugo",
      "name:es", "provincia de Lugo",
      "rank", 3,

      "_type", "point",
      "_minzoom", 2
    )), process(SimpleFeature.create(
      newPoint(0.1, 0.1),
      Map.of(
        "place", "province",
        "wikidata", "Q95027",
        "name", "Lugo"
      ),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      0
    )));
  }

  @Test
  void testIslandPoint() {
    assertFeatures(0, List.of(Map.of(
      "_layer", "place",
      "class", "island",
      "name", "Nantucket",
      "name_en", "Nantucket",
      "name:latin", "Nantucket",
      "rank", 7,

      "_type", "point",
      "_minzoom", 12
    )), process(pointFeature(
      Map.of(
        "place", "island",
        "name", "Nantucket",
        "name:en", "Nantucket"
      ))));
  }

  @Test
  void testIslandPolygon() {
    assertFeatures(0, List.of(Map.of(
      "_layer", "place",
      "class", "island",
      "name", "Nantucket",
      "name_en", "Nantucket",
      "name:latin", "Nantucket",
      "rank", 1,

      "_type", "point",
      "_minzoom", 8
    )), process(polygonFeatureWithArea(1,
      Map.of(
        "place", "island",
        "name", "Nantucket",
        "name:en", "Nantucket"
      ))));

    double rank4area = Math.pow(GeoUtils.metersToPixelAtEquator(0, Math.sqrt(40_000_000 - 1)) / 256d, 2);

    assertFeatures(0, List.of(Map.of(
      "_layer", "place",
      "class", "island",
      "name", "Nantucket",
      "rank", 4,

      "_type", "point",
      "_minzoom", 9
    )), process(polygonFeatureWithArea(rank4area,
      Map.of(
        "place", "island",
        "name", "Nantucket",
        "name:en", "Nantucket"
      ))));
  }

  @Test
  void testIndigenousLand() {
    assertFeatures(0, List.of(Map.of(
      "_layer", "place",
      "class", "aboriginal_lands",
      "name", "Seminole Nation",
      "name_en", "Seminole Nation",
      "name:latin", "Seminole Nation",
      "rank", 1,

      "_type", "point",
      "_minzoom", 6
    ), Map.of(
      "_layer", "boundary"
    )), process(polygonFeatureWithArea(1,
      Map.of(
        "type", "boundary",
        "boundary", "aboriginal_lands",
        "name", "Seminole Nation",
        "name:en", "Seminole Nation"
      ))));

    double rank2area = Math.pow(GeoUtils.metersToPixelAtEquator(0, Math.sqrt(640_000_000 - 1)) / 256d, 2);

    assertFeatures(0, List.of(Map.of(
      "_layer", "place",
      "class", "aboriginal_lands",
      "name", "Seminole Nation",
      "rank", 2,

      "_type", "point",
      "_minzoom", 7
    ), Map.of(
      "_layer", "boundary"
    )), process(polygonFeatureWithArea(rank2area,
      Map.of(
        "type", "boundary",
        "boundary", "aboriginal_lands",
        "name", "Seminole Nation",
        "name:en", "Seminole Nation"
      ))));
  }

  @Test
  void testPlaceSortKeyRanking() {
    int[] sortKeys = new int[]{
      // max
      Place.getSortKey(0, Place.PlaceType.CITY, 1_000_000_000, "name"),

      Place.getSortKey(0, Place.PlaceType.CITY, 1_000_000_000, "name longer"),
      Place.getSortKey(0, Place.PlaceType.CITY, 1_000_000_000, "x".repeat(32)),

      Place.getSortKey(0, Place.PlaceType.CITY, 10_000_000, "name"),
      Place.getSortKey(0, Place.PlaceType.CITY, 0, "name"),

      Place.getSortKey(0, Place.PlaceType.TOWN, 1_000_000_000, "name"),
      Place.getSortKey(0, Place.PlaceType.ISOLATED_DWELLING, 1_000_000_000, "name"),
      Place.getSortKey(0, null, 1_000_000_000, "name"),

      Place.getSortKey(1, Place.PlaceType.CITY, 1_000_000_000, "name"),
      Place.getSortKey(10, Place.PlaceType.CITY, 1_000_000_000, "name"),
      Place.getSortKey(null, Place.PlaceType.CITY, 1_000_000_000, "name"),

      // min
      Place.getSortKey(null, null, 0, null),
    };
    for (int i = 0; i < sortKeys.length; i++) {
      if (sortKeys[i] < SORT_KEY_MIN) {
        fail("Item at index " + i + " is < " + SORT_KEY_MIN + ": " + sortKeys[i]);
      }
      if (sortKeys[i] > SORT_KEY_MAX) {
        fail("Item at index " + i + " is > " + SORT_KEY_MAX + ": " + sortKeys[i]);
      }
    }
    assertAscending(sortKeys);
  }

  @Test
  void testCountryCapital() {
    process(SimpleFeature.create(
      newPoint(0, 0),
      Map.of(
        "name", "Washington, D.C.",
        "scalerank", 0,
        "wikidataid", "Q61"
      ),
      OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
      "ne_10m_populated_places",
      0
    ));
    assertFeatures(7, List.of(Map.of(
      "_layer", "place",
      "class", "city",
      "name", "Washington, D.C.",
      "rank", 1,
      "capital", 2,
      "_labelgrid_limit", 0,
      "_labelgrid_size", 128d,

      "_type", "point",
      "_minzoom", 2
    )), process(pointFeature(
      Map.of(
        "place", "city",
        "name", "Washington, D.C.",
        "population", "672228",
        "wikidata", "Q61",
        "capital", "yes"
      ))));
  }

  @Test
  void testStateCapital() {
    process(SimpleFeature.create(
      newPoint(0, 0),
      Map.of(
        "name", "Boston",
        "scalerank", 2,
        "wikidataid", "Q100"
      ),
      OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
      "ne_10m_populated_places",
      0
    ));
    assertFeatures(0, List.of(Map.of(
      "_layer", "place",
      "class", "city",
      "name", "Boston",
      "rank", 3,
      "capital", 4,

      "_type", "point",
      "_minzoom", 3
    )), process(pointFeature(
      Map.of(
        "place", "city",
        "name", "Boston",
        "population", "667137",
        "capital", "4"
      ))));
    // no match when far away
    assertFeatures(0, List.of(Map.of(
      "_layer", "place",
      "class", "city",
      "name", "Boston",
      "rank", "<null>"
    )), process(SimpleFeature.create(
      newPoint(1, 1),
      Map.of(
        "place", "city",
        "name", "Boston",
        "wikidata", "Q100",
        "population", "667137",
        "capital", "4"
      ),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      0
    )));
    // unaccented name match
    assertFeatures(0, List.of(Map.of(
      "_layer", "place",
      "class", "city",
      "rank", 3
    )), process(pointFeature(
      Map.of(
        "place", "city",
        "name", "Böston",
        "population", "667137",
        "capital", "4"
      ))));
    // wikidata only match
    assertFeatures(0, List.of(Map.of(
      "_layer", "place",
      "class", "city",
      "rank", 3
    )), process(pointFeature(
      Map.of(
        "place", "city",
        "name", "Other name",
        "population", "667137",
        "wikidata", "Q100",
        "capital", "4"
      ))));
  }

  @Test
  void testCountyCapital() {
    process(SimpleFeature.create(
      newPoint(0, 0),
      Map.of(
        "name", "Pueblo",
        "scalerank", 7,
        "wikidataid", "Q675576"
      ),
      OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
      "ne_10m_populated_places",
      0
    ));
    assertFeatures(0, List.of(Map.of(
      "_layer", "place",
      "class", "city",
      "name", "Pueblo",
      "rank", 7,
      "capital", 6,

      "_type", "point",
      "_minzoom", 6
    )), process(pointFeature(
      Map.of(
        "place", "city",
        "name", "Pueblo",
        "population", "111876",
        "capital", "6"
      ))));
    // no match when far away
    assertFeatures(0, List.of(Map.of(
      "_layer", "place",
      "class", "city",
      "name", "Pueblo",
      "rank", "<null>"
    )), process(SimpleFeature.create(
      newPoint(1, 1),
      Map.of(
        "place", "city",
        "name", "Pueblo",
        "wikidata", "Q675576",
        "population", "111876",
        "capital", "6"
      ),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      0
    )));
    // unaccented name match
    assertFeatures(0, List.of(Map.of(
      "_layer", "place",
      "class", "city",
      "rank", 7
    )), process(pointFeature(
      Map.of(
        "place", "city",
        "name", "Pueblo",
        "population", "111876",
        "capital", "6"
      ))));
    // wikidata only match
    assertFeatures(0, List.of(Map.of(
      "_layer", "place",
      "class", "city",
      "rank", 7
    )), process(pointFeature(
      Map.of(
        "place", "city",
        "name", "Other name",
        "population", "111876",
        "wikidata", "Q675576",
        "capital", "6"
      ))));
  }


  @Test
  void testCityWithoutNaturalEarthMatch() {
    assertFeatures(7, List.of(Map.of(
      "_layer", "place",
      "class", "city",
      "rank", "<null>",
      "_minzoom", 7,
      "_labelgrid_limit", 4,
      "_labelgrid_size", 128d
    )), process(pointFeature(
      Map.of(
        "place", "city",
        "name", "City name"
      ))));
    assertFeatures(13, List.of(Map.of(
      "_layer", "place",
      "class", "isolated_dwelling",
      "rank", "<null>",
      "_labelgrid_limit", 0,
      "_labelgrid_size", 0d,
      "_minzoom", 14
    )), process(pointFeature(
      Map.of(
        "place", "isolated_dwelling",
        "name", "City name"
      ))));
    assertFeatures(12, List.of(Map.of(
      "_layer", "place",
      "class", "isolated_dwelling",
      "rank", "<null>",
      "_labelgrid_limit", 14,
      "_labelgrid_size", 128d,
      "_minzoom", 14
    )), process(pointFeature(
      Map.of(
        "place", "isolated_dwelling",
        "name", "City name"
      ))));
  }

  @Test
  void testCitySetRankFromGridrank() throws GeometryException {
    var layerName = Place.LAYER_NAME;
    Assertions.assertEquals(List.of(), profile.postProcessLayerFeatures(layerName, 13, List.of()));

    Assertions.assertEquals(List.of(pointFeature(
      layerName,
      Map.of("rank", 11),
      1
    )), profile.postProcessLayerFeatures(layerName, 13, List.of(pointFeature(
      layerName,
      Map.of(),
      1
    ))));

    Assertions.assertEquals(List.of(
      pointFeature(
        layerName,
        Map.of("rank", 11, "name", "a"),
        1
      ), pointFeature(
        layerName,
        Map.of("rank", 12, "name", "b"),
        1
      ), pointFeature(
        layerName,
        Map.of("rank", 11, "name", "c"),
        2
      )
    ), profile.postProcessLayerFeatures(layerName, 13, List.of(
      pointFeature(
        layerName,
        Map.of("name", "a"),
        1
      ),
      pointFeature(
        layerName,
        Map.of("name", "b"),
        1
      ),
      pointFeature(
        layerName,
        Map.of("name", "c"),
        2
      )
    )));
  }
}
