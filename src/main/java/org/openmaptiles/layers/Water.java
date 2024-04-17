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
import org.locationtech.jts.geom.TopologyException;
import org.locationtech.jts.geom.util.GeometryFixer;
import org.openmaptiles.OpenMapTilesProfile;
import org.openmaptiles.generated.OpenMapTilesSchema;
import org.openmaptiles.generated.Tables;
import org.openmaptiles.util.Utils;

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
  ForwardingProfile.FeaturePostProcessor,
  OpenMapTilesProfile.FinishHandler {

  /*
   * At low zoom levels, use natural earth for oceans and major lakes, and at high zoom levels
   * use OpenStreetMap data. OpenStreetMap data contains smaller bodies of water, but not
   * large ocean polygons. For oceans, use https://osmdata.openstreetmap.de/data/water-polygons.html
   * which infers ocean polygons by preprocessing all coastline elements.
   */

  // smallest NE lake is around 4.42E-13, smallest matching OSM lake is 9.34E-13, this is slightly bellow that
  // and approx. 33% of OSM features are smaller than this, hence to save some CPU cycles:
  private static final double OSM_ID_MATCH_AREA_LIMIT = Math.pow(4, -20);

  private final MultiExpression.Index<String> classMapping;
  private final PlanetilerConfig config;
  private final Stats stats;
  private PolygonIndex<LakeInfo> neLakeIndex = PolygonIndex.create();
  private final Map<String, Map<String, LakeInfo>> neLakeNameMaps = new ConcurrentHashMap<>(); // TODO: simplify in the same way as neLakeIndexes
  private final List<LakeInfo> neAllLakeInfos = new ArrayList<>(); // TODO: once neLakeNameMaps is simplified, we may remove this and use only neLakeNameMap.values()

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
        lakeInfo.geom = geom;
        lakeInfo.name = feature.getString("name");

        var neLakeNameMap = neLakeNameMaps.computeIfAbsent(table, t -> new ConcurrentHashMap<>());

        // need to externally synchronize inserts into the STRTree and ArrayList
        synchronized (this) {
          neLakeIndex.put(geom, lakeInfo);  // TODO: this no longer needs `synchronized`
          neAllLakeInfos.add(lakeInfo);
        }
        if (lakeInfo.name != null) {
          if (!neLakeNameMap.containsKey(lakeInfo.name) ||
            lakeInfo.geom.getArea() > neLakeNameMap.get(lakeInfo.name).geom.getArea()) {
            // on name collision, bigger lake gets on the name list
            neLakeNameMap.put(lakeInfo.name, lakeInfo);
          }
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

      fillOsmIdIntoNeLake(element);
    }
  }

  void fillOsmIdIntoNeLake(Tables.OsmWaterPolygon element) {
    try {
      // match by name:
      boolean match = false;
      if (element.name() != null) {
        for (var map : neLakeNameMaps.values()) {
          var lakeInfo = map.get(element.name());
          if (lakeInfo != null) {
            match = true;
            fillOsmIdIntoNeLake(element, element.source().worldGeometry(), lakeInfo, true);
          }
        }
      }
      if (match) {
        return;
      }

      // if OSM lake is too small for Z6 (e.g. area bellow ~4px) we assume there is no matching NE lake
      Geometry geom = element.source().worldGeometry();
      if (geom.getArea() < OSM_ID_MATCH_AREA_LIMIT) {
        return;
      }

      // match by intersection:
      var items = neLakeIndex.getIntersecting(geom);
      for (var lakeInfo : items) {
        fillOsmIdIntoNeLake(element, geom, lakeInfo, false);
      }
    } catch (GeometryException e) {
      e.log(stats, "omt_water",
        "Error getting geometry for OSM feature " + element.source().id());
    }
  }

  /*
   * When we match lakes with `neLakeIndexes` then `intersetsCheckNeeded` should be `false`,
   * otherwise `true`, to make sure we DO check the intersection but to avoid checking it twice.
   */
  void fillOsmIdIntoNeLake(Tables.OsmWaterPolygon element, Geometry geom, LakeInfo lakeInfo,
    boolean intersetsCheckNeeded) throws GeometryException {
    Geometry neGeom = lakeInfo.geom;
    Geometry intersection;
    try {
      if (intersetsCheckNeeded && !neGeom.intersects(geom)) {
        return;
      }
      intersection = neGeom.intersection(geom);
    } catch (TopologyException e) {
      try {
        Geometry fixedGeom = GeometryFixer.fix(geom);
        if (intersetsCheckNeeded && !neGeom.intersects(fixedGeom)) {
          return;
        }
        intersection = neGeom.intersection(fixedGeom);
      } catch (TopologyException e2) {
        throw new GeometryException("fix_omt_water_topology_error",
          "error fixing polygon: " + e2 + "; original error: " + e);
      }
    }

    // should match following in OpenMapTiles: Distinct on keeps just the first occurence -> order by 'area_ratio DESC'
    double areaRatio = intersection.getArea() / neGeom.getArea();
    if (areaRatio > lakeInfo.areaRatio) {
      lakeInfo.osmId = element.source().id();
      lakeInfo.areaRatio = areaRatio;
    }
  }

  @Override
  public void finish(String sourceName, FeatureCollector.Factory featureCollectors,
    Consumer<FeatureCollector.Feature> emit) {
    if (OpenMapTilesProfile.NATURAL_EARTH_SOURCE.equals(sourceName)) {
      var timer = stats.startStage("ne_lake_index");
      timer.stop();
    } else if (OpenMapTilesProfile.OSM_SOURCE.equals(sourceName)) {
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
    double areaRatio;

    public LakeInfo(int minZoom, int maxZoom, String clazz) {
      this.name = null;
      this.minZoom = minZoom;
      this.maxZoom = maxZoom;
      this.clazz = clazz;
      this.osmId = null;
      this.areaRatio = 0;
    }
  }
}
