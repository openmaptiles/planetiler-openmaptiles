/*
Copyright (c) 2021, MapTiler.com & OpenMapTiles contributors.
All rights reserved.

Code license: BSD 3-Clause License

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

* Redistributions of source code must retain the above copyright notice, this
  list of conditions and the following disclaimer.

* Redistributions in binary form must reproduce the above copyright notice,
  this list of conditions and the following disclaimer in the documentation
  and/or other materials provided with the distribution.

* Neither the name of the copyright holder nor the names of its
  contributors may be used to endorse or promote products derived from
  this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

Design license: CC-BY 4.0

See https://github.com/openmaptiles/openmaptiles/blob/master/LICENSE.md for details on usage
*/
package org.openmaptiles.layers;

import static org.openmaptiles.util.Utils.coalesce;
import static org.openmaptiles.util.Utils.nullIfEmpty;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.config.PlanetilerConfig;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.stats.Stats;
import com.onthegomap.planetiler.util.Parse;
import com.onthegomap.planetiler.util.Translations;
import com.onthegomap.planetiler.util.ZoomFunction;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.openmaptiles.OpenMapTilesProfile;
import org.openmaptiles.generated.OpenMapTilesSchema;
import org.openmaptiles.generated.Tables;

/**
 * Defines the logic for generating map elements for man-made land use polygons like cemeteries, zoos, and hospitals in
 * the {@code landuse} layer from source features.
 * <p>
 * This class is ported to Java from
 * <a href="https://github.com/openmaptiles/openmaptiles/tree/master/layers/landuse">OpenMapTiles landuse sql files</a>.
 */
public class Landuse implements
  OpenMapTilesSchema.Landuse,
  OpenMapTilesProfile.NaturalEarthProcessor,
  OpenMapTilesProfile.FeaturePostProcessor,
  Tables.OsmLandusePolygon.Handler {

  private static final ZoomFunction<Number> MIN_PIXEL_SIZE_THRESHOLDS = ZoomFunction.fromMaxZoomThresholds(Map.of(
    13, 4,
    7, 2,
    6, 1
  ));
  private static final Set<String> Z6_CLASSES = Set.of(
    FieldValues.CLASS_RESIDENTIAL,
    FieldValues.CLASS_SUBURB,
    FieldValues.CLASS_QUARTER,
    FieldValues.CLASS_NEIGHBOURHOOD
  );

  public Landuse(Translations translations, PlanetilerConfig config, Stats stats) {}

  @Override
  public void processNaturalEarth(String table, SourceFeature feature, FeatureCollector features) {
    if ("ne_50m_urban_areas".equals(table)) {
      Double scalerank = Parse.parseDoubleOrNull(feature.getTag("scalerank"));
      if (scalerank != null && scalerank <= 2) {
        features.polygon(LAYER_NAME).setBufferPixels(BUFFER_SIZE)
          .setAttr(Fields.CLASS, FieldValues.CLASS_RESIDENTIAL)
          .setZoomRange(4, 5);
      }
    }
  }

  @Override
  public void process(Tables.OsmLandusePolygon element, FeatureCollector features) {
    String clazz = coalesce(
      nullIfEmpty(element.landuse()),
      nullIfEmpty(element.amenity()),
      nullIfEmpty(element.leisure()),
      nullIfEmpty(element.tourism()),
      nullIfEmpty(element.place()),
      nullIfEmpty(element.waterway())
    );
    if (clazz != null) {
      if ("grave_yard".equals(clazz)) {
        clazz = FieldValues.CLASS_CEMETERY;
      }
      var feature = features.polygon(LAYER_NAME).setBufferPixels(BUFFER_SIZE)
        .setAttr(Fields.CLASS, clazz)
        .setMinZoom(Z6_CLASSES.contains(clazz) ? 6 : 9);
      if (FieldValues.CLASS_RESIDENTIAL.equals(clazz)) {
        feature
          .setMinPixelSize(0.1)
          .setPixelTolerance(0.25);
      } else {
        feature
          .setMinPixelSizeOverrides(MIN_PIXEL_SIZE_THRESHOLDS);
      }
    }
  }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom,
    List<VectorTile.Feature> items) throws GeometryException {
    if (zoom < 6 || zoom > 12) {
      return items;
    } else {
      // merging only merges polygons with class "residential" for z6-z12
      Map<Boolean, List<VectorTile.Feature>> splitLists =
        items.stream().collect(Collectors.partitioningBy(
          i -> FieldValues.CLASS_RESIDENTIAL.equals(i.attrs().get(Fields.CLASS)))
        );
      List<VectorTile.Feature> result = splitLists.get(Boolean.FALSE);
      List<VectorTile.Feature> toMerge = splitLists.get(Boolean.TRUE);
      var merged = FeatureMerge.mergeNearbyPolygons(toMerge, 1, 1, 0.1, 0.1);
      result.addAll(merged);
      return result;
    }
  }
}
