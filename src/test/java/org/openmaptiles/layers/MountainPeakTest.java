package org.openmaptiles.layers;

import static com.onthegomap.planetiler.TestUtils.newPoint;
import static com.onthegomap.planetiler.TestUtils.rectangle;

import com.google.common.collect.Lists;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SimpleFeature;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openmaptiles.OpenMapTilesProfile;

class MountainPeakTest extends AbstractLayerTest {

  @BeforeEach
  public void setupWikidataTranslation() {
    wikidataTranslations.put(123, "es", "es wd name");
  }

  @Test
  void testHappyPath() {
    var peak = process(pointFeature(Map.of(
      "natural", "peak",
      "name", "test",
      "ele", "100",
      "wikidata", "Q123"
    )));
    assertFeatures(14, List.of(Map.of(
      "class", "peak",
      "ele", 100,
      "ele_ft", 328,
      "customary_ft", "<null>",

      "_layer", "mountain_peak",
      "_type", "point",
      "_minzoom", 7,
      "_maxzoom", 14,
      "_buffer", 100d
    )), peak);
    assertFeatures(14, List.of(Map.of(
      "name:latin", "test",
      "name", "test",
      "name:es", "es wd name"
    )), peak);
  }

  @Test
  void testLabelGrid() {
    var peak = process(pointFeature(Map.of(
      "natural", "peak",
      "ele", "100"
    )));
    assertFeatures(14, List.of(Map.of(
      "_labelgrid_limit", 0
    )), peak);
    assertFeatures(13, List.of(Map.of(
      "_labelgrid_limit", 5,
      "_labelgrid_size", 100d
    )), peak);
  }

  @Test
  void testVolcano() {
    assertFeatures(14, List.of(Map.of(
      "class", "volcano"
    )), process(pointFeature(Map.of(
      "natural", "volcano",
      "ele", "100"
    ))));
  }

  @Test
  void testElevationFeet() {
    assertFeatures(14, List.of(Map.of(
      "class", "volcano",
      "ele", 30,
      "ele_ft", 100
    )), process(pointFeature(Map.of(
      "natural", "volcano",
      "ele", "100'"
    ))));
  }

  @Test
  void testElevationFeetInches() {
    assertFeatures(14, List.of(Map.of(
      "class", "volcano",
      "ele", 31,
      "ele_ft", 101
    )), process(pointFeature(Map.of(
      "natural", "volcano",
      "ele", "100' 11\""
    ))));
  }

  @Test
  void testSaddle() {
    assertFeatures(14, List.of(Map.of(
      "class", "saddle"
    )), process(pointFeature(Map.of(
      "natural", "saddle",
      "ele", "100"
    ))));
  }


  @Test
  void testNoElevation() {
    assertFeatures(14, List.of(), process(pointFeature(Map.of(
      "natural", "volcano"
    ))));
  }

  @Test
  void testBogusElevation() {
    assertFeatures(14, List.of(), process(pointFeature(Map.of(
      "natural", "volcano",
      "ele", "11000"
    ))));
  }

  @Test
  void testIgnorePeakLines() {
    assertFeatures(14, List.of(), process(lineFeature(Map.of(
      "natural", "peak",
      "name", "name",
      "ele", "100"
    ))));
  }

  @Test
  void testMountainLinestring() {
    assertFeatures(14, List.of(Map.of(
      "class", "ridge",
      "name", "Ridge",

      "_layer", "mountain_peak",
      "_type", "line",
      "_minzoom", 13,
      "_maxzoom", 14,
      "_buffer", 100d
    )), process(lineFeature(Map.of(
      "natural", "ridge",
      "name", "Ridge"
    ))));
  }

  @Test
  void testCustomaryFt() {
    process(SimpleFeature.create(
      rectangle(0, 0.1),
      Map.of("iso_a2", "US"),
      OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
      "ne_10m_admin_0_countries",
      0
    ));

    // inside US - customary_ft=1
    assertFeatures(14, List.of(Map.of(
      "class", "volcano",
      "customary_ft", 1,
      "ele", 100,
      "ele_ft", 328
    )), process(SimpleFeature.create(
      newPoint(0, 0),
      new HashMap<>(Map.<String, Object>of(
        "natural", "volcano",
        "ele", "100"
      )),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      0
    )));

    // outside US - customary_ft omitted
    assertFeatures(14, List.of(Map.of(
      "class", "volcano",
      "customary_ft", "<null>",
      "ele", 100,
      "ele_ft", 328
    )), process(SimpleFeature.create(
      newPoint(1, 1),
      new HashMap<>(Map.<String, Object>of(
        "natural", "volcano",
        "ele", "100"
      )),
      OpenMapTilesProfile.OSM_SOURCE,
      null,
      0
    )));
  }

  private int getSortKey(Map<String, Object> tags) {
    return process(pointFeature(Map.of(
      "natural", "peak",
      "ele", "100"
    ))).iterator().next().getSortKey();
  }

  @Test
  void testSortKey() {
    assertAscending(
      getSortKey(Map.of(
        "natural", "peak",
        "name", "name",
        "wikipedia", "wikilink",
        "ele", "100"
      )),
      getSortKey(Map.of(
        "natural", "peak",
        "name", "name",
        "ele", "100"
      )),
      getSortKey(Map.of(
        "natural", "peak",
        "ele", "100"
      ))
    );
  }

  @Test
  void testMountainPeakPostProcessing() throws GeometryException {
    Assertions.assertEquals(List.of(), profile.postProcessLayerFeatures(MountainPeak.LAYER_NAME, 13, List.of()));

    Assertions.assertEquals(List.of(pointFeature(
      MountainPeak.LAYER_NAME,
      Map.of("rank", 1),
      1
    )), profile.postProcessLayerFeatures(MountainPeak.LAYER_NAME, 13, List.of(pointFeature(
      MountainPeak.LAYER_NAME,
      Map.of(),
      1
    ))));

    Assertions.assertEquals(List.of(
      pointFeature(
        MountainPeak.LAYER_NAME,
        Map.of("rank", 1, "name", "a"),
        1
      ), pointFeature(
        MountainPeak.LAYER_NAME,
        Map.of("rank", 2, "name", "b"),
        1
      ), pointFeature(
        MountainPeak.LAYER_NAME,
        Map.of("rank", 1, "name", "c"),
        2
      )
    ), profile.postProcessLayerFeatures(MountainPeak.LAYER_NAME, 13, List.of(
      pointFeature(
        MountainPeak.LAYER_NAME,
        Map.of("name", "a"),
        1
      ),
      pointFeature(
        MountainPeak.LAYER_NAME,
        Map.of("name", "b"),
        1
      ),
      pointFeature(
        MountainPeak.LAYER_NAME,
        Map.of("name", "c"),
        2
      )
    )));
  }

  @Test
  void testMountainPeakPostProcessingLimitsFeaturesOutsideZoom() throws GeometryException {
    Assertions.assertEquals(Lists.newArrayList(
      new VectorTile.Feature(
        MountainPeak.LAYER_NAME,
        1,
        VectorTile.encodeGeometry(newPoint(-64, -64)),
        Map.of("rank", 1),
        1
      ),
      null,
      new VectorTile.Feature(
        MountainPeak.LAYER_NAME,
        3,
        VectorTile.encodeGeometry(newPoint(256 + 64, 256 + 64)),
        Map.of("rank", 1),
        2
      ),
      null
    ), profile.postProcessLayerFeatures(MountainPeak.LAYER_NAME, 13, Lists.newArrayList(
      new VectorTile.Feature(
        MountainPeak.LAYER_NAME,
        1,
        VectorTile.encodeGeometry(newPoint(-64, -64)),
        new HashMap<>(),
        1
      ),
      new VectorTile.Feature(
        MountainPeak.LAYER_NAME,
        2,
        VectorTile.encodeGeometry(newPoint(-65, -65)),
        new HashMap<>(),
        1
      ),
      new VectorTile.Feature(
        MountainPeak.LAYER_NAME,
        3,
        VectorTile.encodeGeometry(newPoint(256 + 64, 256 + 64)),
        new HashMap<>(),
        2
      ),
      new VectorTile.Feature(
        MountainPeak.LAYER_NAME,
        4,
        VectorTile.encodeGeometry(newPoint(256 + 65, 256 + 65)),
        new HashMap<>(),
        2
      )
    )));
  }
}
