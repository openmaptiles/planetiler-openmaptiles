package org.openmaptiles;

import com.onthegomap.planetiler.Planetiler;
import com.onthegomap.planetiler.config.Arguments;
import java.nio.file.Path;
import org.openmaptiles.generated.OpenMapTilesSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entrypoint for generating a map using the OpenMapTiles schema.
 */
public class OpenMapTilesMain {

  private static final Logger LOGGER = LoggerFactory.getLogger(OpenMapTilesMain.class);

  public static void main(String[] args) throws Exception {
    run(Arguments.fromArgsOrConfigFile(args));
  }

  static void run(Arguments arguments) throws Exception {
    Path dataDir = Path.of("data");
    Path sourcesDir = arguments.file("download_dir", "download directory", dataDir.resolve("sources"));
    // use --area=... argument, AREA=... env var or area=... in config to set the region of the world to use
    // will be ignored if osm_path or osm_url are set
    String area = arguments.getString(
      "area",
      "name of the extract to download if osm_url/osm_path not specified (i.e. 'monaco' 'rhode island' 'australia' or 'planet')",
      "monaco"
    );

    Planetiler.create(arguments)
      .setDefaultLanguages(OpenMapTilesSchema.LANGUAGES)
      .fetchWikidataNameTranslations(sourcesDir.resolve("wikidata_names.json"))
      // defer creation of the profile because it depends on data from the runner
      .setProfile(OpenMapTilesProfile::new)
      // override any of these with arguments: --osm_path=... or --osm_url=...
      // or OSM_PATH=... OSM_URL=... environmental argument
      // or osm_path=... osm_url=... in a config file
      // Only download the bytes needed from these geoparquet source based on bounds,
      // Converted by: https://github.com/onthegomap/planetiler/blob/main/scripts/shp2parquet.py
      // Upstream: https://github.com/acalcutt/osm-lakelines
      .addParquetSource(OpenMapTilesProfile.LAKE_CENTERLINE_SOURCE,
        sourcesDir.resolve("lake_centerline.parquet"),
        "https://planetiler.onthegomap.com/lake_centerline.parquet")
      // Upstream: https://osmdata.openstreetmap.de/data/water-polygons.html
      .addParquetSource(OpenMapTilesProfile.WATER_POLYGON_SOURCE,
        sourcesDir.resolve("water_polygons.parquet"),
        "https://planetiler.onthegomap.com/water_polygons.parquet")
      .addNaturalEarthSource(OpenMapTilesProfile.NATURAL_EARTH_SOURCE,
        sourcesDir.resolve("natural_earth_vector.sqlite.zip"),
        // upstream is at https://naciscdn.org/naturalearth/packages/natural_earth_vector.sqlite.zip ,
        // following is same URL as used in the OpenMapTiles, a mirror maintained by MapTiler
        "https://dev.maptiler.download/geodata/omt/natural_earth_vector.sqlite.zip")
      .addOsmSource(OpenMapTilesProfile.OSM_SOURCE,
        sourcesDir.resolve(area.replaceAll("[^a-zA-Z]+", "_") + ".osm.pbf"),
        "planet".equalsIgnoreCase(area) ? ("aws:latest") : ("geofabrik:" + area))
      // override with --mbtiles=... argument or MBTILES=... env var or mbtiles=... in a config file
      .setOutput("mbtiles", dataDir.resolve("output.mbtiles"))
      .run();

    LOGGER.info("""
      Acknowledgments
      Generated vector tiles are produced work of OpenStreetMap data.
      Such tiles are reusable under CC-BY license granted by OpenMapTiles team:
      - https://github.com/openmaptiles/openmaptiles/#license
      Maps made with these vector tiles must display a visible credit:
      - © OpenMapTiles © OpenStreetMap contributors
      Thanks to all free, open source software developers and Open Data Contributors!
      """);
  }
}
