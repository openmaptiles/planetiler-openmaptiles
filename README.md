# Planetiler OpenMapTiles Profile

This OpenMapTiles profile for [Planetiler](https://github.com/onthegomap/planetiler) is based
on [OpenMapTiles](https://github.com/openmaptiles/openmaptiles).

## How to run

Using pre-built docker image:

```bash
docker run -v "$(pwd)/data":/data openmaptiles/planetiler-openmaptiles:latest --force --download --area=monaco
```

Or to build from source, after [installing Java 17+](https://adoptium.net/installation.html):

```bash
# Build the project (use mvnw.cmd on windows):
./mvnw clean package
# Then run:
java -jar target/*with-deps.jar --force --download --area=monaco
```

See [Planetiler README.md](https://github.com/onthegomap/planetiler/blob/main/README.md) for more description of the
available options.

## Differences from OpenMapTiles

- Road name abbreviations are not implemented yet in the `transportation_name` layer
- `agg_stop` tag not implemented yet in the `poi` layer
- `brunnel` tag is excluded from `transportation_name` layer to avoid breaking apart long `transportation_name`
  lines, to revert this behavior set `--transportation-name-brunnel=true`
- `rank` field on `mountain_peak` linestrings only has 3 levels (1: has wikipedia page and name, 2: has name, 3: no name
  or wikipedia page or name)

## Customizing

If you want to exclude layers or only include certain layers, then run the project
with  `--exclude-layers=poi,housenumber,...` or `--only-layers=water,transportation,...` command-line arguments.

If you want to customize existing layers in OpenMapTiles, then fork this repo, find the appropriate class from
the [layers package](src/main/java/org/openmaptiles/layers), and make a change to where it processes output features.

<details>
<summary>
Example adding an attribute to a built-in layer
</summary>

For example to copy over the source attribute from OpenStreetMap elements to the building layer,
modify [Building.java](src/main/java/org/openmaptiles/layers/Building.java):

```diff
@@ -166,6 +166,7 @@ public class Building implements
         .setAttrWithMinzoom(Fields.RENDER_MIN_HEIGHT, renderMinHeight, 14)
         .setAttrWithMinzoom(Fields.COLOUR, color, 14)
         .setAttrWithMinzoom(Fields.HIDE_3D, hide3d, 14)
+        .setAttrWithMinzoom("source", element.source().getTag("source"), 14)
         .setSortKey(renderHeight);
       if (mergeZ13Buildings) {
         feature
```

</details>

If you want to generate a mbtiles file with OpenMapTiles base layers plus some extra ones then fork this repo and:

1. Create a new class that implements the [`Layer` interface](src/main/java/org/openmaptiles/Layer.java) in
   the [addons package](src/main/java/org/openmaptiles/addons) and make the `public String name()` method return the ID
   of the new layer.
2. Make the new class implement interfaces from `OpenMapTilesProfile` to register handlers for elements from input
   sources. For example implement `OpenMapTilesProfile.OsmAllProcessor` to handle every OSM element from `processAllOsm`
   method. See the [built-in layers](src/main/java/org/openmaptiles/layers) for examples.
3. Create a new instance of that class from the [`ExtraLayers`](src/main/java/org/openmaptiles/addons/ExtraLayers.java)
   class.

<details>
<summary>
Custom layer example
</summary>

This layer would add a `power` layer to OpenMapTiles output with power lines:

```java
package org.openmaptiles.addons;

import com.onthegomap.planetiler.FeatureCollector;
import com.onthegomap.planetiler.reader.SourceFeature;
import org.openmaptiles.Layer;
import org.openmaptiles.OpenMapTilesProfile;

public class Power implements Layer, OpenMapTilesProfile.OsmAllProcessor {

  private static final String LAYER_NAME = "power";

  @Override
  public String name() {
    return LAYER_NAME;
  }

  @Override
  public void processAllOsm(SourceFeature feature, FeatureCollector features) {
    if (feature.canBeLine() && feature.hasTag("power", "line")) {
      features.line("power")
          .setBufferPixels(4)
          .setMinZoom(6)
          .setAttr("class", "line");
    }
  }
}
```

</details>

If you think your custom layer or change to a built-in layer might be useful to others, consider opening a pull request
to contribute it back to this repo. Any change that diverges from what is produced
by https://github.com/openmaptiles/openmaptiles should be disabled by default, and enabled through a command-line
argument that users can opt-into. For example, see how
the [building layer](src/main/java/org/openmaptiles/layers/Building.java) exposes a `building_merge_z13` command-line
argument to disable merging nearby buildings at z13.

## Code Layout

[Generate.java](src/main/java/org/openmaptiles/Generate.java) generates code in
the [generated](src/main/java/org/openmaptiles/generated) package from an OpenMapTiles tag in
GitHub:

- [OpenMapTilesSchema](src/main/java/org/openmaptiles/generated/OpenMapTilesSchema.java)
  contains an interface for each layer with constants for the name, attributes, and allowed values for each tag in that
  layer
- [Tables](src/main/java/org/openmaptiles/generated/Tables.java)
  contains a record for each table that OpenMapTiles [imposm3](https://github.com/omniscale/imposm3) configuration
  generates (along with the tag-filtering expression) so layers can listen on instances of those records instead of
  doing the tag filtering and parsing themselves

The [layers](src/main/java/org/openmaptiles/layers) package contains a port of the SQL logic to
generate each layer from OpenMapTiles. Layers define how source features (or parsed imposm3 table rows) map to vector
tile features, and logic for post-processing tile geometries.

[OpenMapTilesProfile](src/main/java/org/openmaptiles/OpenMapTilesProfile.java) dispatches source
features to layer handlers and merges the results.

[OpenMapTilesMain](src/main/java/org/openmaptiles/OpenMapTilesMain.java) is the main driver that
registers source data and output location.

## Regenerating Code

To run `Generate.java`,
use [scripts/regenerate-openmaptiles.sh](https://github.com/openmaptiles/planetiler-openmaptiles/blob/main/scripts/regenerate-openmaptiles.sh)
script with the
OpenMapTiles release tag:

```bash
./scripts/regenerate-openmaptiles.sh v3.14
```

Then follow the instructions it prints for reformatting generated code.

If you want to regenerate from a different repository than the default openmaptiles, you can specify the url like this:

```bash
./scripts/regenerate-openmaptiles.sh v3.14 https://raw.githubusercontent.com/openmaptiles/openmaptiles/
```

## License

All code in this repository is under the [BSD license](./LICENSE.md) and the cartography decisions encoded in the schema
and SQL are licensed under [CC-BY](./LICENSE.md).

Products or services using maps derived from OpenMapTiles schema need to visibly credit "OpenMapTiles.org" or
reference "OpenMapTiles" with a link to https://openmaptiles.org/. Exceptions to attribution requirement can be granted
on request.

For a browsable electronic map based on OpenMapTiles and OpenStreetMap data, the
credit should appear in the corner of the map. For example:

[© OpenMapTiles](https://openmaptiles.org/) [© OpenStreetMap contributors](https://www.openstreetmap.org/copyright)

For printed and static maps a similar attribution should be made in a textual
description near the image, in the same fashion as if you cite a photograph.
