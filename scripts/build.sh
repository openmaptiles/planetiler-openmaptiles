#!/usr/bin/env bash

set -o errexit
set -o pipefail
set -o nounset

./mvnw -DskipTests=true clean package
