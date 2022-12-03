package org.openmaptiles;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.onthegomap.planetiler.config.PlanetilerConfig;
import com.onthegomap.planetiler.reader.osm.OsmElement;
import com.onthegomap.planetiler.stats.Stats;
import com.onthegomap.planetiler.util.Translations;
import com.onthegomap.planetiler.util.Wikidata;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenMapTilesProfileTest {

  private final Wikidata.WikidataTranslations wikidataTranslations = new Wikidata.WikidataTranslations();
  private final Translations translations = Translations.defaultProvider(List.of("en", "es", "de"))
    .addFallbackTranslationProvider(wikidataTranslations);
  private final OpenMapTilesProfile profile = new OpenMapTilesProfile(translations, PlanetilerConfig.defaults(),
    Stats.inMemory());

  @Test
  void testCaresAboutWikidata() {
    var node = new OsmElement.Node(1, 1, 1);
    node.setTag("aeroway", "gate");
    assertTrue(profile.caresAboutWikidataTranslation(node));

    node.setTag("aeroway", "other");
    assertFalse(profile.caresAboutWikidataTranslation(node));
  }

  @Test
  void testDoesntCareAboutWikidataForRoads() {
    var way = new OsmElement.Way(1);
    way.setTag("highway", "footway");
    assertFalse(profile.caresAboutWikidataTranslation(way));
  }
}
