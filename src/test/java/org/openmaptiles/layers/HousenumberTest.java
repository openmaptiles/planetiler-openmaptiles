package org.openmaptiles.layers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

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

  @ParameterizedTest
  @CsvSource({
      "1, 1",
      "1;1a;2;2/b;20;3, 1–3",
      "1;1a;2;2/b;20;3;, 1–3",
      "1;2;20;3, 1–20",
      "1;2;20;3;, 1–20",
      ";, ;",
      ";;, ;;",
      "2712;935803935803, 2712–935803935803",
  })
  void testDisplayHousenumber(String outlier, String expected) {
    assertEquals(expected, Housenumber.displayHousenumber(outlier));
  }
}
