#!/usr/bin/env bash

set -eu

if (( $# != 1 )); then
  echo "Usage: set_versions.sh <version>" >&2
  exit 1
fi

version="$1"

./mvnw -B -ntp versions:set versions:commit -DnewVersion="${version}"
