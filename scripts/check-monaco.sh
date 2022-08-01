#!/usr/bin/env bash

set -o errexit
set -o pipefail
set -o nounset

java -ea -cp target/*-with-deps.jar org.openmaptiles.util.VerifyMonaco $*
