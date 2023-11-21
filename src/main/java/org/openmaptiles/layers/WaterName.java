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

import com.carrotsearch.hppc.LongObjectMap;
import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.collection.Hppc;
import com.onthegomap.planetiler.config.PlanetilerConfig;
import com.onthegomap.planetiler.geo.GeoUtils;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.stats.Stats;
import com.onthegomap.planetiler.util.Parse;
import com.onthegomap.planetiler.util.Translations;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import org.locationtech.jts.geom.Geometry;
import org.openmaptiles.OpenMapTilesProfile;
import org.openmaptiles.generated.OpenMapTilesSchema;
import org.openmaptiles.generated.Tables;
import org.openmaptiles.util.OmtLanguageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines the logic for generating map elements for ocean and lake names in the {@code water_name} layer from source
 * features.
 * <p>
 * This class is ported to Java from
 * <a href="https://github.com/openmaptiles/openmaptiles/tree/master/layers/water_name">OpenMapTiles water_name sql
 * files</a>.
 */
public class WaterName implements
  OpenMapTilesSchema.WaterName,
  Tables.OsmMarinePoint.Handler,
  Tables.OsmWaterPolygon.Handler,
  OpenMapTilesProfile.NaturalEarthProcessor,
  OpenMapTilesProfile.LakeCenterlineProcessor {

  /*
   * Labels for lakes and oceans come primarily from OpenStreetMap data, but we also join
   * with the lake centerlines source to get linestring geometries for prominent lakes.
   * We also join with natural earth to make certain important lake/ocean labels visible
   * at lower zoom levels.
   */

  private static final Logger LOGGER = LoggerFactory.getLogger(WaterName.class);
  private static final Set<String> SEA_OR_OCEAN_PLACE = Set.of("sea", "ocean");
  private final Translations translations;
  // need to synchronize updates from multiple threads
  private final LongObjectMap<Geometry> lakeCenterlines = Hppc.newLongObjectHashMap();
  // may be updated concurrently by multiple threads
  private final ConcurrentSkipListMap<String, Integer> importantMarinePoints = new ConcurrentSkipListMap<>();
  private final Stats stats;

  public WaterName(Translations translations, PlanetilerConfig config, Stats stats) {
    this.translations = translations;
    this.stats = stats;
  }

  @Override
  public void release() {
    lakeCenterlines.release();
    importantMarinePoints.clear();
  }

  @Override
  public void processLakeCenterline(SourceFeature feature, FeatureCollector features) {
    // TODO pull lake centerline computation into planetiler?
    long osmId = Math.abs(feature.getLong("OSM_ID"));
    if (osmId == 0L) {
      LOGGER.warn("Bad lake centerline. Tags: {}", feature.tags());
    } else {
      try {
        // multiple threads call this concurrently
        synchronized (this) {
          // if we already have a centerline for this OSM_ID, then merge the existing one with this one
          var newGeometry = feature.worldGeometry();
          var oldGeometry = lakeCenterlines.get(osmId);
          if (oldGeometry != null) {
            newGeometry = GeoUtils.combine(oldGeometry, newGeometry);
          }
          lakeCenterlines.put(osmId, newGeometry);
        }
      } catch (GeometryException e) {
        e.log(stats, "omt_water_name_lakeline", "Bad lake centerline: " + feature);
      }
    }
  }

  @Override
  public void processNaturalEarth(String table, SourceFeature feature, FeatureCollector features) {
    // use natural earth named polygons just as a source of name to zoom-level mappings for later
    if ("ne_10m_geography_marine_polys".equals(table)) {
      String name = feature.getString("name");
      Integer scalerank = Parse.parseIntOrNull(feature.getTag("scalerank"));
      if (name != null && scalerank != null) {
        name = name.replaceAll("\\s+", " ").trim().toLowerCase();
        importantMarinePoints.put(name, scalerank);
      }
    }
  }

  @Override
  public void process(Tables.OsmMarinePoint element, FeatureCollector features) {
    if (!element.name().isBlank()) {
      String clazz = coalesce(
        nullIfEmpty(element.natural()),
        nullIfEmpty(element.place())
      );
      var source = element.source();
      // use name from OSM, but get min zoom from natural earth based on fuzzy name match...
      Integer rank = Parse.parseIntOrNull(source.getTag("rank"));
      String name = element.name().toLowerCase();
      Integer nerank;
      if ((nerank = importantMarinePoints.get(name)) != null) {
        rank = nerank;
      } else if ((nerank = importantMarinePoints.get(source.getString("name:en", "").toLowerCase())) != null) {
        rank = nerank;
      } else if ((nerank = importantMarinePoints.get(source.getString("name:es", "").toLowerCase())) != null) {
        rank = nerank;
      } else {
        Map.Entry<String, Integer> next = importantMarinePoints.ceilingEntry(name);
        if (next != null && next.getKey().startsWith(name)) {
          rank = next.getValue();
        }
      }
      int minZoom;
      if ("ocean".equals(element.place())) {
        minZoom = 0;
      } else if (rank != null) {
        // FIXME: While this looks like matching properly stuff in https://github.com/openmaptiles/openmaptiles/pull/1457/files#diff-201daa1c61c99073fe3280d440c9feca5ed2236b251ad454caa14cc203f952d1R74 ,
        // it includes not just https://www.openstreetmap.org/relation/13360255 but also https://www.openstreetmap.org/node/1385157299 (and some others).
        // Hence check how that OpenMapTiles code works for "James Bay" and:
        // a) if same as here then, fix there and then here
        // b) if OK (while here NOK), fix only here
        minZoom = rank;
      } else if ("bay".equals(element.natural())) {
        minZoom = 13;
      } else {
        minZoom = 8;
      }
      features.point(LAYER_NAME)
        .setBufferPixels(BUFFER_SIZE)
        .putAttrs(OmtLanguageUtils.getNames(source.tags(), translations))
        .setAttr(Fields.CLASS, clazz)
        .setAttr(Fields.INTERMITTENT, element.isIntermittent() ? 1 : 0)
        .setMinZoom(minZoom);
    }
  }

  @Override
  public void process(Tables.OsmWaterPolygon element, FeatureCollector features) {
    if (nullIfEmpty(element.name()) != null) {
      Geometry centerlineGeometry = lakeCenterlines.get(element.source().id());
      FeatureCollector.Feature feature;
      int minzoom = 9;
      String place = element.place();
      String clazz;
      if ("bay".equals(element.natural())) {
        clazz = FieldValues.CLASS_BAY;
      } else if ("sea".equals(place)) {
        clazz = FieldValues.CLASS_SEA;
      } else {
        clazz = FieldValues.CLASS_LAKE;
        minzoom = 3;
      }
      if (centerlineGeometry != null) {
        // prefer lake centerline if it exists
        feature = features.geometry(LAYER_NAME, centerlineGeometry)
          .setMinPixelSizeBelowZoom(13, 6d * element.name().length());
      } else {
        // otherwise just use a label point inside the lake
        feature = features.pointOnSurface(LAYER_NAME)
          .setMinZoom(place != null && SEA_OR_OCEAN_PLACE.contains(place) ? 0 : 3)
          .setMinPixelSize(128); // tiles are 256x256, so 128x128 is 1/4 of a tile
      }
      feature
        .setAttr(Fields.CLASS, clazz)
        .setBufferPixels(BUFFER_SIZE)
        .putAttrs(OmtLanguageUtils.getNames(element.source().tags(), translations))
        .setAttr(Fields.INTERMITTENT, element.isIntermittent() ? 1 : 0)
        .setMinZoom(minzoom);
    }
  }
}
