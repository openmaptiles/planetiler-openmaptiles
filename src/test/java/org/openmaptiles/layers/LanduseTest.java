package org.openmaptiles.layers;

import static com.onthegomap.planetiler.TestUtils.rectangle;

import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.geo.GeoUtils;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SimpleFeature;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.openmaptiles.OpenMapTilesProfile;

class LanduseTest extends AbstractLayerTest {

  @Test
  void testNaturalEarthUrbanAreas() {
    assertFeatures(0, List.of(Map.of(
      "_layer", "landuse",
      "class", "residential",
      "_buffer", 4d,
      "_minzoom", 4
    )), process(SimpleFeature.create(
      GeoUtils.worldToLatLonCoords(rectangle(0, Math.sqrt(1))),
      Map.of("scalerank", 1.9),
      OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
      "ne_50m_urban_areas",
      0
    )));
    assertFeatures(0, List.of(Map.of(
      "_layer", "landuse",
      "class", "residential",
      "_buffer", 4d,
      "_minzoom", 5
    )), process(SimpleFeature.create(
      GeoUtils.worldToLatLonCoords(rectangle(0, Math.sqrt(1))),
      Map.of("scalerank", 2.1),
      OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
      "ne_50m_urban_areas",
      0
    )));
  }

  @Test
  void testOsmLanduse() {
    assertFeatures(13, List.of(
      Map.of("_layer", "poi"),
      Map.of(
        "_layer", "landuse",
        "class", "railway",
        "_minpixelsize", 4d,
        "_minzoom", 9,
        "_maxzoom", 14
      )), process(polygonFeature(Map.of(
        "landuse", "railway",
        "amenity", "school"
      ))));
    assertFeatures(13, List.of(Map.of("_layer", "poi"), Map.of(
      "_layer", "landuse",
      "class", "school",
      "_minpixelsize", 4d,
      "_minzoom", 9,
      "_maxzoom", 14
    )), process(polygonFeature(Map.of(
      "amenity", "school"
    ))));
  }

  @Test
  void testGraveYardBecomesCemetery() {
    assertFeatures(14, List.of(
      Map.of("_layer", "poi"),
      Map.of(
        "_layer", "landuse",
        "class", "cemetery"
      )), process(polygonFeature(Map.of(
        "amenity", "grave_yard"
      ))));
  }

  @Test
  void testOsmLanduseLowerZoom() {
    assertFeatures(6, List.of(Map.of(
      "_layer", "landuse",
      "class", "suburb",
      "_minzoom", 6,
      "_maxzoom", 14,
      "_minpixelsize", 1d
    )), process(polygonFeature(Map.of(
      "place", "suburb"
    ))));
    assertFeatures(7, List.of(Map.of(
      "_layer", "landuse",
      "class", "residential",
      "_minzoom", 6,
      "_maxzoom", 14,
      "_minpixelsize", 0.1d
    )), process(polygonFeature(Map.of(
      "landuse", "residential"
    ))));
  }

  @Test
  void testMergePolygonsZ12() throws GeometryException {
    var poly1 = new VectorTile.Feature(
      Landuse.LAYER_NAME,
      1,
      VectorTile.encodeGeometry(rectangle(10, 20)),
      Map.of("class", "residential"),
      0
    );
    var poly2 = new VectorTile.Feature(
      Landuse.LAYER_NAME,
      1,
      VectorTile.encodeGeometry(rectangle(20, 10, 22, 20)),
      Map.of("class", "residential"),
      0
    );
    var poly3 = new VectorTile.Feature(
      Landuse.LAYER_NAME,
      1,
      VectorTile.encodeGeometry(rectangle(10, 20, 20, 22)),
      Map.of("class", "suburb"),
      0
    );

    Assertions.assertEquals(
      List.of(1, 2),
      profile.postProcessLayerFeatures(Landuse.LAYER_NAME, 13, List.of(poly1, poly2, poly3)).stream()
        .map(d -> {
          try {
            return d.geometry().decode().getNumGeometries();
          } catch (GeometryException e) {
            throw new AssertionError(e);
          }
        }).toList()
    );
    Assertions.assertEquals(
      List.of(1, 1),
      profile.postProcessLayerFeatures(Landuse.LAYER_NAME, 12, List.of(poly1, poly2, poly3)).stream()
        .map(d -> {
          try {
            return d.geometry().decode().getNumGeometries();
          } catch (GeometryException e) {
            throw new AssertionError(e);
          }
        }).toList()
    );
  }
}
