#!/usr/bin/env bash

set -exuo pipefail

version="${1:-$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)}"

echo "Test java build"
echo "::group::OpenMapTiles monaco (java)"
java -jar target/*with-deps.jar --download --area=monaco --mbtiles=data/java.mbtiles
./scripts/check-monaco.sh data/java.mbtiles
echo "::endgroup::"

echo "::endgroup::"
echo "::group::OpenMapTiles monaco (docker)"
rm -f data/docker.mbtiles
docker run -v "$(pwd)/data":/data openmaptiles/planetiler-openmaptiles:"${version}" --area=monaco --mbtiles=data/docker.mbtiles
./scripts/check-monaco.sh data/docker.mbtiles
echo "::endgroup::"

echo "::group::Compare"
java -cp target/*with-deps.jar com.onthegomap.planetiler.util.CompareArchives data/java.mbtiles data/docker.mbtiles
echo "::endgroup::"