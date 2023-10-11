package org.openmaptiles.layers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HousenumberTest extends AbstractLayerTest {

  @Test
  void testHousenumber() {
    assertFeatures(14, List.of(Map.of(
      "_layer", "housenumber",
      "_type", "point",
      "_minzoom", 14,
      "_maxzoom", 14,
      "_buffer", 8d
    )), process(pointFeature(Map.of(
      "addr:housenumber", "10"
    ))));
    assertFeatures(14, List.of(Map.of(
      "_layer", "housenumber",
      "_type", "point",
      "_minzoom", 14,
      "_maxzoom", 14,
      "_buffer", 8d
    )), process(polygonFeature(Map.of(
      "addr:housenumber", "10"
    ))));
  }

  @Test
  void testDisplayHousenumberNonRange() {
    final String HOUSENUMBER = "1";
    assertEquals(HOUSENUMBER, Housenumber.displayHousenumber(HOUSENUMBER));
  }

  @Test
  void testDisplayHousenumberNonnumericRange() {
    final String HOUSENUMBER = "1;1a;2;2/b;20;3";
    assertEquals("1–3", Housenumber.displayHousenumber(HOUSENUMBER));
  }

  @Test
  void testDisplayHousenumberNonnumericRangeBroken() {
    final String HOUSENUMBER = "1;1a;2;2/b;20;3;";
    assertEquals("1–3", Housenumber.displayHousenumber(HOUSENUMBER));
  }

  @Test
  void testDisplayHousenumberNumericRange() {
    final String HOUSENUMBER = "1;2;20;3";
    assertEquals("1–20", Housenumber.displayHousenumber(HOUSENUMBER));
  }

  @Test
  void testDisplayHousenumberNumericRangeBroken() {
    final String HOUSENUMBER = "1;2;20;3;";
    assertEquals("1–20", Housenumber.displayHousenumber(HOUSENUMBER));
  }

  @Test
  void testDisplayHousenumberExtraBroken() {
    final String HOUSENUMBER_1 = ";";
    assertEquals(HOUSENUMBER_1, Housenumber.displayHousenumber(HOUSENUMBER_1));

    final String HOUSENUMBER_2 = ";;";
    assertEquals(HOUSENUMBER_2, Housenumber.displayHousenumber(HOUSENUMBER_2));
  }
}
