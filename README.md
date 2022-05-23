# Planetiler OpenMapTiles Profile

This OpenMapTiles profile is based on [OpenMapTiles](https://github.com/openmaptiles/openmaptiles).
See [README.md](https://github.com/onthegomap/planetiler/blob/main/README.md) in the parent repository for instructions on how to run.

## Differences from OpenMapTiles

- Road name abbreviations are not implemented yet in the `transportation_name` layer
- `agg_stop` tag not implemented yet in the `poi` layer
- `brunnel` tag is excluded from `transportation_name` layer to avoid breaking apart long `transportation_name`
  lines, to revert this behavior set `--transportation-name-brunnel=true`
- `rank` field on `mountain_peak` linestrings only has 3 levels (1: has wikipedia page and name, 2: has name, 3: no name
  or wikipedia page or name)

## Code Layout

[Generate.java](./src/main/java/com/onthegomap/planetiler/openmaptiles/Generate.java) generates code in
the [generated](./src/main/java/com/onthegomap/planetiler/openmaptiles/generated) package from an OpenMapTiles tag in GitHub:

- [OpenMapTilesSchema](./src/main/java/com/onthegomap/planetiler/openmaptiles/generated/OpenMapTilesSchema.java)
  contains an interface for each layer with constants for the name, attributes, and allowed values for each tag in that
  layer
- [Tables](./src/main/java/com/onthegomap/planetiler/openmaptiles/generated/Tables.java)
  contains a record for each table that OpenMapTiles [imposm3](https://github.com/omniscale/imposm3) configuration
  generates (along with the tag-filtering expression) so layers can listen on instances of those records instead of
  doing the tag filtering and parsing themselves

The [layers](./src/main/java/com/onthegomap/planetiler/openmaptiles/layers) package contains a port of the SQL logic to
generate each layer from OpenMapTiles. Layers define how source features (or parsed imposm3 table rows) map to vector
tile features, and logic for post-processing tile geometries.

[OpenMapTilesProfile](./src/main/java/com/onthegomap/planetiler/openmaptiles/OpenMapTilesProfile.java) dispatches source features to
layer handlers and merges the results.

[OpenMapTilesMain](./src/main/java/com/onthegomap/planetiler/openmaptiles/OpenMapTilesMain.java) is the main driver that registers
source data and output location.

## Regenerating Code

To run `Generate.java`, use [scripts/regenerate-openmaptiles.sh](https://github.com/onthegomap/planetiler/blob/main/scripts/regenerate-openmaptiles.sh) script with the
OpenMapTiles release tag:

```bash
./scripts/regenerate-openmaptiles.sh v3.13.1
```

Then follow the instructions it prints for reformatting generated code.

If you want to regenerate from a different repository than the default openmaptiles, you can specify the url like this:

```bash
./scripts/regenerate-openmaptiles.sh v3.13.1 https://raw.githubusercontent.com/openmaptiles/openmaptiles/
```

## License

All code in this repository is under the [BSD license](./LICENSE.md) and the cartography decisions encoded in the schema and SQL are licensed under [CC-BY](./LICENSE.md).

Products or services using maps derived from OpenMapTiles schema need to visibly credit "OpenMapTiles.org" or reference "OpenMapTiles" with a link to https://openmaptiles.org/. Exceptions to attribution requirement can be granted on request.

For a browsable electronic map based on OpenMapTiles and OpenStreetMap data, the
credit should appear in the corner of the map. For example:

[© OpenMapTiles](https://openmaptiles.org/) [© OpenStreetMap contributors](https://www.openstreetmap.org/copyright)

For printed and static maps a similar attribution should be made in a textual
description near the image, in the same fashion as if you cite a photograph.
