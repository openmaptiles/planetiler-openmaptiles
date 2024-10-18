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

import static java.util.Map.entry;
import static org.openmaptiles.util.Utils.coalesce;
import static org.openmaptiles.util.Utils.nullIfEmpty;
import static org.openmaptiles.util.Utils.nullIfLong;
import static org.openmaptiles.util.Utils.nullOrEmpty;

import com.carrotsearch.hppc.LongIntMap;
import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.collection.Hppc;
import com.onthegomap.planetiler.config.PlanetilerConfig;
import com.onthegomap.planetiler.expression.MultiExpression;
import com.onthegomap.planetiler.geo.GeoUtils;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.SimpleFeature;
import com.onthegomap.planetiler.stats.Stats;
import com.onthegomap.planetiler.util.Parse;
import com.onthegomap.planetiler.util.Translations;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.locationtech.jts.geom.Point;
import org.openmaptiles.OpenMapTilesProfile;
import org.openmaptiles.generated.OpenMapTilesSchema;
import org.openmaptiles.generated.Tables;
import org.openmaptiles.util.OmtLanguageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines the logic for generating map elements for things like shops, parks, and schools in the {@code poi} layer from
 * source features.
 * <p>
 * This class is ported to Java from
 * <a href="https://github.com/openmaptiles/openmaptiles/tree/master/layers/poi">OpenMapTiles poi sql files</a>.
 */
public class Poi implements
  OpenMapTilesSchema.Poi,
  Tables.OsmPoiPoint.Handler,
  Tables.OsmPoiPolygon.Handler,
  ForwardingProfile.LayerPostProcessor,
  ForwardingProfile.FinishHandler {

  /*
   * process() creates the raw POI feature from OSM elements and postProcess()
   * assigns the feature rank from order in the tile at render-time.
   */

  private static final Logger LOGGER = LoggerFactory.getLogger(Poi.class);
  private static final Map<String, Integer> CLASS_RANKS = Map.ofEntries(
    entry(FieldValues.CLASS_HOSPITAL, 20),
    entry(FieldValues.CLASS_RAILWAY, 40),
    entry(FieldValues.CLASS_BUS, 50),
    entry(FieldValues.CLASS_ATTRACTION, 70),
    entry(FieldValues.CLASS_HARBOR, 75),
    entry(FieldValues.CLASS_COLLEGE, 80),
    entry(FieldValues.CLASS_SCHOOL, 85),
    entry(FieldValues.CLASS_STADIUM, 90),
    entry("zoo", 95),
    entry(FieldValues.CLASS_TOWN_HALL, 100),
    entry(FieldValues.CLASS_CAMPSITE, 110),
    entry(FieldValues.CLASS_CEMETERY, 115),
    entry(FieldValues.CLASS_PARK, 120),
    entry(FieldValues.CLASS_LIBRARY, 130),
    entry("police", 135),
    entry(FieldValues.CLASS_POST, 140),
    entry(FieldValues.CLASS_GOLF, 150),
    entry(FieldValues.CLASS_SHOP, 400),
    entry(FieldValues.CLASS_GROCERY, 500),
    entry(FieldValues.CLASS_FAST_FOOD, 600),
    entry(FieldValues.CLASS_CLOTHING_STORE, 700),
    entry(FieldValues.CLASS_BAR, 800)
  );
  private static final Set<String> UNIVERSITY_POI_SUBCLASSES = Set.of("university", "college");
  private static final List<String> AGG_STOP_SUBCLASS_ORDER = List.of(
    "subway",
    "tram_stop",
    "bus_station",
    "bus_stop"
  );
  private static final Comparator<Tables.OsmPoiPoint> BY_SUBCLASS = Comparator
    .comparingInt(s -> AGG_STOP_SUBCLASS_ORDER.indexOf(s.subclass()));
  private static final Set<String> BRAND_OPERATOR_REF_SUBCLASSES = Set.of("charging_station", "parcel_locker");
  private final MultiExpression.Index<String> classMapping;
  private final Translations translations;
  private final Stats stats;
  private final Map<String, List<Tables.OsmPoiPoint>> aggStops = new HashMap<>();

  public Poi(Translations translations, PlanetilerConfig config, Stats stats) {
    this.classMapping = FieldMappings.Class.index();
    this.translations = translations;
    this.stats = stats;
  }

  static int poiClassRank(String clazz) {
    return CLASS_RANKS.getOrDefault(clazz, 1_000);
  }

  private String poiClass(String subclass, String mappingKey) {
    // Special case subclass collision between office=university and amenity=university
    if ("amenity".equals(mappingKey) && "university".equals(subclass)) {
      return FieldValues.CLASS_COLLEGE;
    }

    subclass = coalesce(subclass, "");
    return classMapping.getOrElse(Map.of(
      "subclass", subclass,
      "mapping_key", coalesce(mappingKey, "")
    ), subclass);
  }

  private int minzoom(String subclass, String mappingKey) {
    boolean lowZoom = ("station".equals(subclass) && "railway".equals(mappingKey)) ||
      "halt".equals(subclass) || "ferry_terminal".equals(subclass);
    return lowZoom ? 12 : 14;
  }

  @Override
  public void release() {
    aggStops.clear();
  }

  @Override
  public void process(Tables.OsmPoiPoint element, FeatureCollector features) {
    if (element.uicRef() != null && AGG_STOP_SUBCLASS_ORDER.contains(element.subclass())) {
      // multiple threads may update this concurrently
      String aggStopKey = element.uicRef()
        .concat(coalesce(nullIfEmpty(element.name()), ""))
        .concat(coalesce(nullIfEmpty(element.network()), ""))
        .concat(coalesce(nullIfEmpty(element.operator()), ""));
      synchronized (this) {
        aggStops.computeIfAbsent(aggStopKey, key -> new ArrayList<>()).add(element);
      }
    } else {
      setupPoiFeature(element, features.point(LAYER_NAME), null);
    }
  }

  private void processAggStop(Tables.OsmPoiPoint element, FeatureCollector.Factory featureCollectors,
    Consumer<FeatureCollector.Feature> emit, Integer aggStop) {
    try {
      var features =
        featureCollectors.get(SimpleFeature.fromWorldGeometry(element.source().worldGeometry(), element.source().id()));
      setupPoiFeature(element, features.point(LAYER_NAME), aggStop);
      for (var feature : features) {
        emit.accept(feature);
      }
    } catch (GeometryException e) {
      e.log(stats, "agg_stop_geometry_2",
        "Error getting geometry for the stop " + element.source().id() + " (agg_stop)");
    }
  }

  /**
   * We've put aside some stops for {@code agg_stop} processing and we do that processing here.
   * <p>
   * The main point is to group together stops with same {@code uid_ref} and then order them first based on subclass
   * (see {@code AGG_STOP_ORDER}) and then based on distance from centroid (calculated from all the stops). The first
   * one gets {@code agg_stop=1}, the rest will be "normal" (e.g. no {@code agg_stop} attribute).
   * <p>
   * ref: <a href=
   * "https://github.com/openmaptiles/openmaptiles/blob/master/layers/poi/poi_stop_agg.sql#L26,L28">poi_stop_agg.sql</a>
   */
  @Override
  public void finish(String sourceName, FeatureCollector.Factory featureCollectors,
    Consumer<FeatureCollector.Feature> emit) {
    if (OpenMapTilesProfile.OSM_SOURCE.equals(sourceName)) {
      var timer = stats.startStage("agg_stop");
      LOGGER.info("Processing {} agg_stop sets", aggStops.size());

      for (var aggStopSet : aggStops.values()) {
        if (aggStopSet.size() == 1) {
          processAggStop(aggStopSet.getFirst(), featureCollectors, emit, 1);
          continue;
        }
        Tables.OsmPoiPoint nearest = null;
        try {
          // find most important stops based on subclass
          var firstSubclass = aggStopSet.stream().min(BY_SUBCLASS).get().subclass();
          var topAggStops =
            aggStopSet.stream().filter(s -> firstSubclass.equals(s.subclass())).toArray(Tables.OsmPoiPoint[]::new);

          // calculate the centroid and ...
          List<Point> aggStopPoints = new ArrayList<>(aggStopSet.size());
          for (var aggStop : aggStopSet) {
            aggStopPoints.add(aggStop.source().worldGeometry().getCentroid());
          }
          var aggStopCentroid = GeoUtils.combinePoints(aggStopPoints).getCentroid();

          // ... find one stop nearest to the centroid
          double minDistance = Double.MAX_VALUE;
          for (var aggStop : topAggStops) {
            double distance = aggStopCentroid.distance(aggStop.source().worldGeometry());
            if (distance < minDistance || nearest == null ||
              (distance == minDistance && aggStop.source().id() < nearest.source().id())) {
              minDistance = distance;
              nearest = aggStop;
            }
          }
        } catch (GeometryException e) {
          e.log(stats, "agg_stop_geometry_1",
            "Error getting geometry for some of the stops with UIC ref. " + aggStopSet.getFirst().uicRef() +
              " (agg_stop)");
          // we're not able to calculate agg_stop, so simply dump the stops as they are
          nearest = null;
        }

        // now emit the stops
        final Tables.OsmPoiPoint nearestFinal = nearest; // final needed for lambda
        aggStopSet
          .forEach(s -> processAggStop(s, featureCollectors, emit, s == nearestFinal ? 1 : null));
      }

      timer.stop();
    }
  }

  @Override
  public void process(Tables.OsmPoiPolygon element, FeatureCollector features) {
    setupPoiFeature(element, features.centroidIfConvex(LAYER_NAME), null);
  }

  private <T extends Tables.WithSubclass & Tables.WithStation & Tables.WithFunicular & Tables.WithSport & Tables.WithInformation & Tables.WithReligion & Tables.WithMappingKey & Tables.WithName & Tables.WithIndoor & Tables.WithLayer & Tables.WithSource & Tables.WithOperator & Tables.WithNetwork & Tables.WithBrand & Tables.WithRef> void setupPoiFeature(
    T element, FeatureCollector.Feature output, Integer aggStop) {
    String rawSubclass = element.subclass();
    if ("station".equals(rawSubclass) && "subway".equals(element.station())) {
      rawSubclass = "subway";
    }
    if ("station".equals(rawSubclass) && "yes".equals(element.funicular())) {
      rawSubclass = "halt";
    }

    // ATM names fall back to operator, or else network
    String name = element.name();
    var tags = element.source().tags();
    if ("atm".equals(rawSubclass) && nullOrEmpty(name)) {
      name = coalesce(nullIfEmpty(element.operator()), nullIfEmpty(element.network()));
      if (name != null) {
        tags.put("name", name);
      }
    }

    // Parcel locker without name: use either brand or operator and add ref if present
    if (BRAND_OPERATOR_REF_SUBCLASSES.contains(rawSubclass) && nullOrEmpty(name)) {
      name = coalesce(nullIfEmpty(element.brand()), nullIfEmpty(element.operator()));
      String ref = nullIfEmpty(element.ref());
      if (ref != null) {
        name = name == null ? ref : (name + " " + ref);
      }
      if (name != null) {
        tags.put("name", name);
      }
    }

    String subclass = switch (rawSubclass) {
      case "information" -> nullIfEmpty(element.information());
      case "place_of_worship" -> nullIfEmpty(element.religion());
      case "pitch" -> nullIfEmpty(element.sport());
      default -> rawSubclass;
    };
    String poiClass = poiClass(rawSubclass, element.mappingKey());
    int poiClassRank = poiClassRank(poiClass);
    int rankOrder = poiClassRank + ((nullOrEmpty(name)) ? 2000 : 0);

    int minzoom = minzoom(element.subclass(), element.mappingKey());
    if (UNIVERSITY_POI_SUBCLASSES.contains(rawSubclass)) {
      // universities that are at least 10% of a tile may appear from Z10
      output.setMinPixelSizeBelowZoom(13, 80); // 80x80px is ~10% of a 256x256px tile
      minzoom = 10;
    }

    output.setBufferPixels(BUFFER_SIZE)
      .setAttr(Fields.CLASS, poiClass)
      .setAttr(Fields.SUBCLASS, subclass)
      .setAttr(Fields.LAYER, nullIfLong(element.layer(), 0))
      .setAttr(Fields.LEVEL, Parse.parseLongOrNull(element.source().getTag("level")))
      .setAttr(Fields.INDOOR, element.indoor() ? 1 : null)
      .setAttr(Fields.AGG_STOP, aggStop)
      .putAttrs(OmtLanguageUtils.getNames(element.source().tags(), translations))
      .setPointLabelGridPixelSize(14, 64)
      .setSortKey(rankOrder)
      .setMinZoom(minzoom);
  }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> items) {
    // infer the "rank" field from the order of features within each label grid square
    LongIntMap groupCounts = Hppc.newLongIntHashMap();
    for (VectorTile.Feature feature : items) {
      int gridrank = groupCounts.getOrDefault(feature.group(), 1);
      groupCounts.put(feature.group(), gridrank + 1);
      if (!feature.tags().containsKey(Fields.RANK)) {
        feature.tags().put(Fields.RANK, gridrank);
      }
    }
    return items;
  }
}
