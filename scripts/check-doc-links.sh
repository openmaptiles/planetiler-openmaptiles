#!/usr/bin/env bash

set -o errexit
set -o pipefail
set -o nounset

find . -name '*.md' -not -path '*/target/*' -print0 | xargs -I {} -n 1 -0 markdown-link-check --quiet --config .github/workflows/docs_mlc_config.json {}
