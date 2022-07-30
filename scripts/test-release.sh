#!/usr/bin/env bash

set -exuo pipefail

version="${1:-$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)}"

echo "Test java build"
echo "::group::OpenMapTiles monaco (java)"
rm -f data/out.mbtiles
java -jar target/*with-deps.jar --download --area=monaco --mbtiles=data/out.mbtiles
./scripts/check-monaco.sh data/out.mbtiles
echo "::endgroup::"

echo "::endgroup::"
echo "::group::OpenMapTiles monaco (docker)"
rm -f data/out.mbtiles
docker run -v "$(pwd)/data":/data ghcr.io/openmaptiles/planetiler-openmaptiles:"${version}" --area=monaco --mbtiles=data/out.mbtiles
./scripts/check-monaco.sh data/out.mbtiles
echo "::endgroup::"
