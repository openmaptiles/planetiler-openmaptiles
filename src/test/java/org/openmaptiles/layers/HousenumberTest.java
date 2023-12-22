package org.openmaptiles.layers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.onthegomap.planetiler.geo.GeometryException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
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

  @Test
  void testTempAttrs() {
    assertFeatures(14, List.of(Map.of(
      "_has_name", Boolean.TRUE,
      "_partition", "streetX765/6"
    )), process(polygonFeature(Map.of(
      "addr:housenumber", "765/6",
      "addr:block_number", "X",
      "addr:street", "street",
      "name", "name"
    ))));
  }

  @Test
  void testNonduplicateHousenumber() throws GeometryException {
    var layerName = Housenumber.LAYER_NAME;
    var hn1 = pointFeature(
      layerName,
      Map.of(
        "housenumber", "764/2",
        "_partition", "764/2"
      ),
      1
    );
    var hn2 = pointFeature(
      layerName,
      Map.of(
        "housenumber", "765/6",
        "_partition", "765/6"
      ),
      1
    );

    Assertions.assertEquals(
      2,
      profile.postProcessLayerFeatures(layerName, 14, List.of(hn1, hn2)).size()
    );
  }

  @Test
  void testNonduplicateStreet() throws GeometryException {
    var layerName = Housenumber.LAYER_NAME;
    var housenumber = "765/6";
    var hn1 = pointFeature(
      layerName,
      Map.of(
        "housenumber", housenumber,
        "_partition", "street 1" + housenumber
      ),
      1
    );
    var hn2 = pointFeature(
      layerName,
      Map.of(
        "housenumber", housenumber,
        "_partition", "street 2" + housenumber
      ),
      1
    );

    var result = profile.postProcessLayerFeatures(layerName, 14, List.of(hn1, hn2));

    Assertions.assertEquals(
      1, // same housenumber => two points merged into one multipoint
      result.size()
    );
    Assertions.assertEquals(
      5, // two point in multipoint => 5 commands
      result.getFirst().geometry().commands().length);
  }

  @Test
  void testDuplicateHousenumber() throws GeometryException {
    var layerName = Housenumber.LAYER_NAME;
    var housenumber = "765/6";
    var hn1 = pointFeature(
      layerName,
      Map.of(
        "housenumber", housenumber + " (no name)",
        "_has_name", false,
        "_partition", housenumber
      ),
      1
    );
    var hn2 = pointFeature(
      layerName,
      Map.of(
        "housenumber", housenumber + " (with name)",
        "_has_name", true,
        "_partition", housenumber
      ),
      1
    );

    var result = profile.postProcessLayerFeatures(layerName, 14, List.of(hn1, hn2));

    Assertions.assertEquals(List.of(
      pointFeature(
        layerName,
        Map.of("housenumber", "765/6 (no name)"),
        1
      )
    ), result);
    Assertions.assertEquals(
      3, // only one point in multipoint => 3 commands
      result.getFirst().geometry().commands().length);
  }
}
