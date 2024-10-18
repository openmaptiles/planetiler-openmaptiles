/*
Copyright (c) 2024, MapTiler.com & OpenMapTiles contributors.
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

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.config.PlanetilerConfig;
import com.onthegomap.planetiler.expression.MultiExpression;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.geo.PolygonIndex;
import com.onthegomap.planetiler.reader.SimpleFeature;
import com.onthegomap.planetiler.reader.SourceFeature;
import com.onthegomap.planetiler.stats.Stats;
import com.onthegomap.planetiler.util.Translations;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.util.GeometryFixer;
import org.openmaptiles.OpenMapTilesProfile;
import org.openmaptiles.generated.OpenMapTilesSchema;
import org.openmaptiles.generated.Tables;
import org.openmaptiles.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines the logic for generating map elements for oceans and lakes in the {@code water} layer from source features.
 * <p>
 * This class is ported to Java from
 * <a href="https://github.com/openmaptiles/openmaptiles/tree/master/layers/water">OpenMapTiles water sql files</a>.
 */
public class Water implements
  OpenMapTilesSchema.Water,
  Tables.OsmWaterPolygon.Handler,
  OpenMapTilesProfile.NaturalEarthProcessor,
  OpenMapTilesProfile.OsmWaterPolygonProcessor,
  ForwardingProfile.LayerPostProcessor,
  ForwardingProfile.FinishHandler {

  /*
   * At low zoom levels, use natural earth for oceans and major lakes, and at high zoom levels
   * use OpenStreetMap data. OpenStreetMap data contains smaller bodies of water, but not
   * large ocean polygons. For oceans, use https://osmdata.openstreetmap.de/data/water-polygons.html
   * which infers ocean polygons by preprocessing all coastline elements.
   */

  private static final Logger LOGGER = LoggerFactory.getLogger(Water.class);
  // smallest NE lake is around 4.42E-13, smallest matching OSM lake is 9.34E-13, this is slightly bellow that
  // and approx. 33% of OSM features are smaller than this, hence to save some CPU cycles:
  private static final double OSM_ID_MATCH_AREA_LIMIT = Math.pow(4, -20);

  private final MultiExpression.Index<String> classMapping;
  private final PlanetilerConfig config;
  private final Stats stats;
  private PolygonIndex<LakeInfo> neLakeIndex = PolygonIndex.create();
  private final Map<String, Map<String, LakeInfo>> neLakeNameMaps = new ConcurrentHashMap<>();
  private final List<LakeInfo> neAllLakeInfos = new ArrayList<>();

  public Water(Translations translations, PlanetilerConfig config, Stats stats) {
    this.classMapping = FieldMappings.Class.index();
    this.config = config;
    this.stats = stats;
  }

  @Override
  public void processNaturalEarth(String table, SourceFeature feature, FeatureCollector features) {
    record WaterInfo(int minZoom, int maxZoom, String clazz) {}
    WaterInfo info = switch (table) {
      case "ne_110m_ocean" -> new WaterInfo(0, 1, FieldValues.CLASS_OCEAN);
      case "ne_50m_ocean" -> new WaterInfo(2, 4, FieldValues.CLASS_OCEAN);
      case "ne_10m_ocean" -> new WaterInfo(5, 5, FieldValues.CLASS_OCEAN);
      default -> null;
    };
    if (info != null) {
      setupNeWaterFeature(features, info.minZoom, info.maxZoom, info.clazz, null);
      return;
    }

    LakeInfo lakeInfo = switch (table) {
      case "ne_110m_lakes" -> new LakeInfo(0, 1, FieldValues.CLASS_LAKE);
      case "ne_50m_lakes" -> new LakeInfo(2, 3, FieldValues.CLASS_LAKE);
      case "ne_10m_lakes" -> new LakeInfo(4, 5, FieldValues.CLASS_LAKE);
      default -> null;
    };
    if (lakeInfo != null) {
      try {
        var geom = feature.worldGeometry();
        if (geom.isValid()) {
          lakeInfo.geom = geom;
        } else {
          LOGGER.trace("Fixing geometry of NE lake {}", feature.getLong("ne_id"));
          lakeInfo.geom = GeometryFixer.fix(geom);
        }
        lakeInfo.name = feature.getString("name");
        lakeInfo.neId = feature.getLong("ne_id");

        var neLakeNameMap = neLakeNameMaps.computeIfAbsent(table, t -> new ConcurrentHashMap<>());

        // need to externally synchronize inserts into ArrayList
        synchronized (this) {
          neAllLakeInfos.add(lakeInfo);
        }
        neLakeIndex.put(geom, lakeInfo);
        if (lakeInfo.name != null) {
          // on name collision, bigger lake gets on the name list
          neLakeNameMap.merge(lakeInfo.name, lakeInfo,
            (prev, next) -> next.geom.getArea() > prev.geom.getArea() ? next : prev);
        }
      } catch (GeometryException e) {
        e.log(stats, "omt_water_ne",
          "Error getting geometry for natural earth feature " + table + " " + feature.getTag("ogc_fid"));
        // make sure we have this NE lake even if without OSM ID
        setupNeWaterFeature(features, lakeInfo.minZoom, lakeInfo.maxZoom, lakeInfo.clazz, null);
      }
    }
  }

  private void setupNeWaterFeature(FeatureCollector features, int minZoom, int maxZoom, String clazz, Long osmId) {
    features.polygon(LAYER_NAME)
      .setBufferPixels(BUFFER_SIZE)
      .setZoomRange(minZoom, maxZoom)
      .setAttr(Fields.CLASS, clazz)
      .setAttr(Fields.ID, osmId);
  }

  @Override
  public void processOsmWater(SourceFeature feature, FeatureCollector features) {
    features.polygon(LAYER_NAME)
      .setBufferPixels(BUFFER_SIZE)
      .setAttr(Fields.CLASS, FieldValues.CLASS_OCEAN)
      .setMinZoom(6);
  }

  @Override
  public void process(Tables.OsmWaterPolygon element, FeatureCollector features) {
    if (!"bay".equals(element.natural())) {
      String clazz = classMapping.getOrElse(element.source(), FieldValues.CLASS_LAKE);
      features.polygon(LAYER_NAME)
        .setBufferPixels(BUFFER_SIZE)
        .setMinPixelSizeBelowZoom(11, 2)
        .setMinZoom(6)
        .setAttr(Fields.ID, element.source().id())
        .setAttr(Fields.INTERMITTENT, element.isIntermittent() ? 1 : 0)
        .setAttrWithMinzoom(Fields.BRUNNEL, Utils.brunnel(element.isBridge(), element.isTunnel()), 12)
        .setAttr(Fields.CLASS, clazz);

      try {
        attemptNeLakeIdMapping(element);
      } catch (GeometryException e) {
        e.log(stats, "omt_water",
          "Unable to add OSM ID to natural earth water feature", config.logJtsExceptions());
      }
    }
  }

  void attemptNeLakeIdMapping(Tables.OsmWaterPolygon element) throws GeometryException {
    // if OSM lake is too small for Z6 (e.g. area bellow ~4px) we assume there is no matching NE lake
    var geom = element.source().worldGeometry();
    if (geom.getArea() < OSM_ID_MATCH_AREA_LIMIT) {
      return;
    }

    if (!geom.isValid()) {
      geom = GeometryFixer.fix(geom);
      stats.dataError("omt_fix_water_before_ne_intersect");
      LOGGER.trace("Fixing geometry of OSM element {} before attempt to add ID to natural earth water feature",
        element.source().id());
    }

    // match by name:
    boolean match = false;
    if (element.name() != null) {
      for (var map : neLakeNameMaps.values()) {
        var lakeInfo = map.get(element.name());
        if (lakeInfo != null) {
          match = true;
          fillOsmIdIntoNeLake(element, geom, lakeInfo, true);
        }
      }
    }
    if (match) {
      return;
    }

    // match by intersection:
    List<LakeInfo> items = neLakeIndex.getIntersecting(geom);
    for (var lakeInfo : items) {
      fillOsmIdIntoNeLake(element, geom, lakeInfo, false);
    }
  }

  /*
   * When we match lakes with `neLakeIndexes` then `intersetsCheckNeeded` should be `false`,
   * otherwise `true`, to make sure we DO check the intersection but to avoid checking it twice.
   */
  void fillOsmIdIntoNeLake(Tables.OsmWaterPolygon element, Geometry geom, LakeInfo lakeInfo,
    boolean intersetsCheckNeeded) {
    final Geometry neGeom = lakeInfo.geom;
    if (intersetsCheckNeeded && !neGeom.intersects(geom)) {
      return;
    }
    final var intersection = neGeom.intersection(geom);

    // Should match following in OpenMapTiles: Distinct on keeps just the first occurence -> order by 'area_ratio DESC'
    // With a twist: NE geometry is always the same, hence we can make it a little bit faster by dropping "ratio"
    // and compare only the intersection area: bigger area -> bigger ratio.
    double area = intersection.getArea();
    lakeInfo.mergeId(element.source().id(), area);
  }

  @Override
  public void finish(String sourceName, FeatureCollector.Factory featureCollectors,
    Consumer<FeatureCollector.Feature> emit) {
    if (OpenMapTilesProfile.OSM_SOURCE.equals(sourceName)) {
      var timer = stats.startStage("ne_lakes");
      for (var item : neAllLakeInfos) {
        var features = featureCollectors.get(SimpleFeature.fromWorldGeometry(item.geom));
        setupNeWaterFeature(features, item.minZoom, item.maxZoom, item.clazz, item.osmId);
        for (var feature : features) {
          emit.accept(feature);
        }
      }
      neLakeNameMaps.clear();
      neLakeIndex = null;
      neAllLakeInfos.clear();
      timer.stop();
    }
  }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items) throws GeometryException {
    return items.size() > 1 ? FeatureMerge.mergeOverlappingPolygons(items, config.minFeatureSize(zoom)) : items;
  }

  /**
   * Information to hold onto from processing an NE lake to determine OSM ID later.
   */
  private static class LakeInfo {
    String name;
    int minZoom;
    int maxZoom;
    String clazz;
    Geometry geom;
    Long osmId;
    long neId;
    double area;

    public LakeInfo(int minZoom, int maxZoom, String clazz) {
      this.name = null;
      this.minZoom = minZoom;
      this.maxZoom = maxZoom;
      this.clazz = clazz;
      this.osmId = null;
      this.neId = -1;
      this.area = 0;
    }

    public synchronized void mergeId(Long newId, double newArea) {
      if (newArea > area) {
        osmId = newId;
        area = newArea;
      }
    }
  }
}
