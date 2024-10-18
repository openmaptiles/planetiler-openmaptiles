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
import com.onthegomap.planetiler.geo.GeometryException;
import com.onthegomap.planetiler.stats.Stats;
import com.onthegomap.planetiler.util.Translations;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.openmaptiles.generated.OpenMapTilesSchema;
import org.openmaptiles.generated.Tables;
import org.openmaptiles.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Defines the logic for generating map elements in the {@code housenumber} layer from source features.
 * <p>
 * This class is ported to Java from
 * <a href="https://github.com/openmaptiles/openmaptiles/tree/master/layers/housenumber">OpenMapTiles housenumber sql
 * files</a>.
 */
public class Housenumber implements
  OpenMapTilesSchema.Housenumber,
  Tables.OsmHousenumberPoint.Handler,
  ForwardingProfile.LayerPostProcessor {

  private static final Logger LOGGER = LoggerFactory.getLogger(Housenumber.class);
  private static final String OSM_SEPARATOR = ";";
  private static final String DISPLAY_SEPARATOR = "–";
  private static final Pattern NO_CONVERSION_PATTERN = Pattern.compile("[^0-9;]");
  private static final String TEMP_PARTITION = "_partition";
  private static final String TEMP_HAS_NAME = "_has_name";
  private static final Comparator<VectorTile.Feature> BY_TEMP_HAS_NAME = Comparator
    .comparing(i -> (Boolean) i.tags().get(TEMP_HAS_NAME), Boolean::compare);
  private final Stats stats;

  public Housenumber(Translations translations, PlanetilerConfig config, Stats stats) {
    this.stats = stats;
  }

  private static String displayHousenumberNonumeric(List<String> numbers) {
    return numbers.getFirst()
      .concat(DISPLAY_SEPARATOR)
      .concat(numbers.getLast());
  }

  protected static String displayHousenumber(String housenumber) {
    if (!housenumber.contains(OSM_SEPARATOR)) {
      return housenumber;
    }

    List<String> numbers = Arrays.stream(housenumber.split(OSM_SEPARATOR))
      .map(String::trim)
      .filter(Predicate.not(String::isEmpty))
      .toList();
    if (numbers.isEmpty()) {
      // not much to do with strange/invalid entries like "3;" or ";" etc.
      return housenumber;
    }

    Matcher matcher = NO_CONVERSION_PATTERN.matcher(housenumber);
    if (matcher.find()) {
      return displayHousenumberNonumeric(numbers);
    }

    // numeric display house number
    var statistics = numbers.stream()
      .collect(Collectors.summarizingLong(Long::parseUnsignedLong));
    return String.valueOf(statistics.getMin())
      .concat(DISPLAY_SEPARATOR)
      .concat(String.valueOf(statistics.getMax()));
  }

  @Override
  public void process(Tables.OsmHousenumberPoint element, FeatureCollector features) {
    String housenumber;
    try {
      housenumber = displayHousenumber(element.housenumber());
    } catch (NumberFormatException e) {
      // should not be happening (thanks to NO_CONVERSION_PATTERN) but ...
      stats.dataError("housenumber_range");
      LOGGER.warn("Failed to convert housenumber range: {}", element.housenumber());
      housenumber = element.housenumber();
    }

    String partition = Utils.coalesce(element.street(), "")
      .concat(Utils.coalesce(element.blockNumber(), ""))
      .concat(housenumber);
    Boolean hasName = element.hasName() == null ? Boolean.FALSE : !element.hasName().isEmpty();

    features.centroidIfConvex(LAYER_NAME)
      .setBufferPixels(BUFFER_SIZE)
      .setAttr(Fields.HOUSENUMBER, housenumber)
      .setAttr(TEMP_PARTITION, partition)
      .setAttr(TEMP_HAS_NAME, hasName)
      .setMinZoom(14);
  }

  @Override
  public List<VectorTile.Feature> postProcess(int zoom, List<VectorTile.Feature> list) throws GeometryException {
    // remove duplicate house numbers, features without name tag are prioritized
    var items = list.stream()
      .collect(Collectors.groupingBy(f -> f.tags().get(TEMP_PARTITION)))
      .values().stream()
      .flatMap(
        g -> g.stream().min(BY_TEMP_HAS_NAME).stream()
      )
      .toList();

    // remove temporary attributes
    for (var item : items) {
      item.tags().remove(TEMP_HAS_NAME);
      item.tags().remove(TEMP_PARTITION);
    }

    // reduces the size of some heavy z14 tiles with many repeated housenumber values by 60% or more
    return FeatureMerge.mergeMultiPoint(items);
  }
}
