/*
Copyright (c) 2023, MapTiler.com & OpenMapTiles contributors.
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

import static com.onthegomap.planetiler.util.MemoryEstimator.CLASS_HEADER_BYTES;
import static com.onthegomap.planetiler.util.Parse.parseDoubleOrNull;
import static java.util.Map.entry;
import static org.openmaptiles.util.Utils.coalesce;
import static org.openmaptiles.util.Utils.nullIfEmpty;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.FeatureMerge;
import com.onthegomap.planetiler.ForwardingProfile;
import com.onthegomap.planetiler.VectorTile;
import com.onthegomap.planetiler.config.PlanetilerConfig;
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.reader.osm.OsmRelationInfo;
import com.onthegomap.planetiler.stats.Stats;
import com.onthegomap.planetiler.util.MemoryEstimator;
import com.onthegomap.planetiler.util.Parse;
import com.onthegomap.planetiler.util.Translations;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFilter;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.geom.TopologyException;
import org.locationtech.jts.index.strtree.STRtree;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;
import org.openmaptiles.generated.OpenMapTilesSchema;
import org.openmaptiles.generated.Tables;

/**
 * Defines the logic for generating map elements for buildings in the {@code building} layer from source features.
 * <p>
 * This class is ported to Java from
 * <a href="https://github.com/openmaptiles/openmaptiles/tree/master/layers/building">OpenMapTiles building sql
 * files</a>.
 */
public class Building implements
  OpenMapTilesSchema.Building,
  Tables.OsmBuildingPolygon.Handler,
  ForwardingProfile.LayerPostProcessor,
  ForwardingProfile.OsmRelationPreprocessor {

  /*
   * Emit all buildings from OSM data at z14.
   *
   * At z13, emit all buildings at process-time, but then at tile render-time,
   * merge buildings that are overlapping or almost touching into combined
   * buildings so that entire city blocks show up as a single building polygon.
   *
   * THIS IS VERY EXPENSIVE! Merging buildings at z13 adds about 50% to the
   * total map generation time.  To disable it, set building_merge_z13 argument
   * to false.
   */

  private static final Map<String, String> MATERIAL_COLORS = Map.ofEntries(
    entry("cement_block", "#6a7880"),
    entry("brick", "#bd8161"),
    entry("plaster", "#dadbdb"),
    entry("wood", "#d48741"),
    entry("concrete", "#d3c2b0"),
    entry("metal", "#b7b1a6"),
    entry("stone", "#b4a995"),
    entry("mud", "#9d8b75"),
    entry("steel", "#b7b1a6"), // same as metal
    entry("glass", "#5a81a0"),
    entry("traditional", "#bd8161"), // same as brick
    entry("masonry", "#bd8161"), // same as brick
    entry("Brick", "#bd8161"), // same as brick
    entry("tin", "#b7b1a6"), // same as metal
    entry("timber_framing", "#b3b0a9"),
    entry("sandstone", "#b4a995"), // same as stone
    entry("clay", "#9d8b75") // same as mud
  );
  private static final String TEMP_PART = "_part";
  /** Share of a building's area that its parts have to cover before the building is hidden in 3D. */
  private static final double MIN_PART_COVERAGE = 0.5;
  private final boolean mergeZ13Buildings;
  private final boolean hideOutlinesWithParts;

  public Building(Translations translations, PlanetilerConfig config, Stats stats) {
    this.mergeZ13Buildings = config.arguments().getBoolean(
      "building_merge_z13",
      "building layer: merge nearby buildings at z13",
      true
    );
    this.hideOutlinesWithParts = config.arguments().getBoolean(
      "building_hide_outlines_with_parts",
      "building layer: set hide_3d on buildings that are mostly covered by building parts",
      false
    );
  }

  @Override
  public List<OsmRelationInfo> preprocessOsmRelation(OsmElement.Relation relation) {
    if (relation.hasTag("type", "building")) {
      return List.of(new BuildingRelationInfo(relation.id()));
    }
    return null;
  }

  @Override
  public void process(Tables.OsmBuildingPolygon element, FeatureCollector features) {
    Boolean hide3d = null;
    var relations = element.source().relationInfo(BuildingRelationInfo.class);
    for (var relation : relations) {
      if ("outline".equals(relation.role())) {
        hide3d = true;
        break;
      }
    }

    String color = element.colour();
    if (color == null && element.material() != null) {
      color = MATERIAL_COLORS.get(element.material());
    }
    if (color != null) {
      color = color.toLowerCase(Locale.ROOT);
    }

    Double height = Parse.meters(coalesce(
      nullIfEmpty(element.height()),
      nullIfEmpty(element.buildingheight())
    ));
    Double minHeight = Parse.meters(coalesce(
      nullIfEmpty(element.minHeight()),
      nullIfEmpty(element.buildingminHeight())
    ));
    Double levels = coalesce(
      parseDoubleOrNull(element.levels()),
      parseDoubleOrNull(element.buildinglevels())
    );
    Double minLevels = coalesce(
      parseDoubleOrNull(element.minLevel()),
      parseDoubleOrNull(element.buildingminLevel())
    );

    int renderHeight = (int) Math.ceil(height != null ? height : levels != null ? (levels * 3.66) : 5);
    int renderMinHeight = (int) Math.floor(minHeight != null ? minHeight : minLevels != null ? (minLevels * 3.66) : 0);

    if (renderHeight < 3660 && renderMinHeight < 3660) {
      var feature = features.polygon(LAYER_NAME).setBufferPixels(BUFFER_SIZE)
        .setMinZoom(13)
        .setMinPixelSize(2)
        .setAttrWithMinzoom(Fields.RENDER_HEIGHT, renderHeight, 14)
        .setAttrWithMinzoom(Fields.RENDER_MIN_HEIGHT, renderMinHeight, 14)
        .setAttrWithMinzoom(Fields.COLOUR, color, 14)
        .setAttrWithMinzoom(Fields.HIDE_3D, hide3d, 14)
        .setAttrWithMinzoom(TEMP_PART, hideOutlinesWithParts && element.buildingpart() != null ? true : null, 14)
        .setSortKey(renderHeight);
      if (mergeZ13Buildings) {
        feature
          .setMinPixelSize(0.1)
          .setPixelTolerance(0.25);
      }
    }
  }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom,
    List<VectorTile.Feature> items) throws GeometryException {
    if (hideOutlinesWithParts && zoom >= 14) {
      hideOutlinesCoveredByParts(items);
    }
    return (mergeZ13Buildings && zoom == 13) ?
      FeatureMerge.mergeNearbyPolygons(items, 4, 4, 0.5, 0.5) :
      // reduces the size of some heavy z14 tiles with many small buildings by 60% or more
      FeatureMerge.mergeMultiPolygon(items);
  }

  /**
   * Sets {@code hide_3d} on buildings that are mostly covered by {@code building:part}s, because
   * <a href="https://wiki.openstreetmap.org/wiki/Simple_3D_Buildings">Simple 3D Buildings</a> does not use the outline
   * of a building for 3D rendering when it has parts.
   */
  private static void hideOutlinesCoveredByParts(List<VectorTile.Feature> items) throws GeometryException {
    List<VectorTile.Feature> outlines = new ArrayList<>();
    STRtree parts = new STRtree();
    for (var item : items) {
      if (!item.tags().containsKey(TEMP_PART)) {
        outlines.add(item);
        continue;
      }
      item.tags().remove(TEMP_PART);
      Geometry geometry = item.geometry().decode();
      geometry.apply((GeometryFilter) (Geometry geom) -> {
        if (geom instanceof Polygon part) {
          parts.insert(part.getEnvelopeInternal(), part);
        }
      });
    }
    if (parts.isEmpty()) {
      return;
    }
    for (var outline : outlines) {
      if (Boolean.TRUE.equals(outline.tags().get(Fields.HIDE_3D))) {
        continue;
      }
      if (partCoverage(outline.geometry().decode(), parts) > MIN_PART_COVERAGE) {
        outline.tags().put(Fields.HIDE_3D, true);
      }
    }
  }

  /** Returns the share of {@code outline}'s area that is covered by {@code parts}. */
  private static double partCoverage(Geometry outline, STRtree parts) {
    List<Geometry> nearby = new ArrayList<>();
    for (Object part : parts.query(outline.getEnvelopeInternal())) {
      nearby.add((Geometry) part);
    }
    if (nearby.isEmpty()) {
      return 0;
    }
    try {
      // the default intersection throws a TopologyException on some real buildings that OverlayNGRobust handles
      Geometry covered = OverlayNGRobust.overlay(outline, OverlayNGRobust.union(nearby), OverlayNG.INTERSECTION);
      return covered.getArea() / outline.getArea();
    } catch (TopologyException e) {
      return 0;
    }
  }

  private record BuildingRelationInfo(long id) implements OsmRelationInfo {

    @Override
    public long estimateMemoryUsageBytes() {
      return CLASS_HEADER_BYTES + MemoryEstimator.estimateSizeLong(id);
    }
  }
}
