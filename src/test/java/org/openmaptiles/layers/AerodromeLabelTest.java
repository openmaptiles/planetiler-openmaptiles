package org.openmaptiles.layers;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AerodromeLabelTest extends AbstractLayerTest {

  @BeforeEach
  public void setupWikidataTranslation() {
    wikidataTranslations.put(123, "es", "es wd name");
  }

  @Test
  void testIntlWithIata() {
    assertFeatures(14, List.of(Map.of(
      "class", "international",
      "ele", 100,
      "ele_ft", 328,
      "name", "osm name",
      "name:es", "es wd name",

      "_layer", "aerodrome_label",
      "_type", "point",
      "_minzoom", 8,
      "_maxzoom", 14,
      "_buffer", 64d
    )), process(pointFeature(Map.of(
      "aeroway", "aerodrome",
      "name", "osm name",
      "wikidata", "Q123",
      "ele", "100",
      "aerodrome", "international",
      "iata", "123",
      "icao", "1234"
    ))));
  }

  @Test
  void testElevationFeet() {
    assertFeatures(14, List.of(Map.of(
      "ele", 100,
      "ele_ft", 328
    )), process(pointFeature(Map.of(
      "aeroway", "aerodrome",
      "name", "osm name",
      "ele", "328'",
      "aerodrome", "international",
      "iata", "123",
      "icao", "1234"
    ))));
  }

  @Test
  void testElevationFeetInches() {
    assertFeatures(14, List.of(Map.of(
      "ele", 100,
      "ele_ft", 328
    )), process(pointFeature(Map.of(
      "aeroway", "aerodrome",
      "name", "osm name",
      "ele", "328' 1\"",
      "aerodrome", "international",
      "iata", "123",
      "icao", "1234"
    ))));
  }

  @Test
  void testInternational() {
    assertFeatures(14, List.of(Map.of(
      "class", "international",
      "_layer", "aerodrome_label",
      "_minzoom", 10 // no IATA
    )), process(pointFeature(Map.of(
      "aeroway", "aerodrome",
      "aerodrome_type", "international"
    ))));
  }

  @Test
  void testPublic() {
    assertFeatures(14, List.of(Map.of(
      "class", "public",
      "_layer", "aerodrome_label",
      "_minzoom", 10
    )), process(pointFeature(Map.of(
      "aeroway", "aerodrome",
      "aerodrome_type", "public airport"
    ))));
    assertFeatures(14, List.of(Map.of(
      "class", "public",
      "_layer", "aerodrome_label"
    )), process(pointFeature(Map.of(
      "aeroway", "aerodrome",
      "aerodrome_type", "civil"
    ))));
  }

  @Test
  void testMilitary() {
    assertFeatures(14, List.of(Map.of(
      "class", "military",
      "_layer", "aerodrome_label",
      "_minzoom", 10
    )), process(pointFeature(Map.of(
      "aeroway", "aerodrome",
      "aerodrome_type", "military airport"
    ))));
    assertFeatures(14, List.of(Map.of(
      "class", "military",
      "_layer", "aerodrome_label"
    )), process(pointFeature(Map.of(
      "aeroway", "aerodrome",
      "military", "airfield"
    ))));
  }

  @Test
  void testPrivate() {
    assertFeatures(14, List.of(Map.of(
      "class", "private",
      "_layer", "aerodrome_label",
      "_minzoom", 10
    )), process(pointFeature(Map.of(
      "aeroway", "aerodrome",
      "aerodrome_type", "private"
    ))));
    assertFeatures(14, List.of(Map.of(
      "class", "private",
      "_layer", "aerodrome_label"
    )), process(pointFeature(Map.of(
      "aeroway", "aerodrome",
      "aerodrome", "private"
    ))));
  }

  @Test
  void testOther() {
    assertFeatures(14, List.of(Map.of(
      "class", "other",
      "_layer", "aerodrome_label",
      "_minzoom", 10
    )), process(pointFeature(Map.of(
      "aeroway", "aerodrome"
    ))));
  }

  @Test
  void testIgnoreNonPoints() {
    assertFeatures(14, List.of(), process(lineFeature(Map.of(
      "aeroway", "aerodrome"
    ))));
  }
}
