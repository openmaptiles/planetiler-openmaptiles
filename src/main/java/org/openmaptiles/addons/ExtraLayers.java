package org.openmaptiles.addons;

import com.onthegomap.planetiler.config.PlanetilerConfig;
import com.onthegomap.planetiler.stats.Stats;
import com.onthegomap.planetiler.util.Translations;
import java.util.List;
import org.openmaptiles.Layer;

/**
 * Registry of extra custom layers that you can add to the openmaptiles schema.
 */
public class ExtraLayers {

  public static List<Layer> create(Translations translations, PlanetilerConfig config, Stats stats) {
    return List.of(
      // Create classes that extend Layer interface in the addons package, then instantiate them here
    );
  }
}
