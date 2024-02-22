#!/usr/bin/env bash

set -euxo pipefail

mixcr exportHelp ../cli-help
mixcr exportAllPresets ../presets
mixcr exportSchemas ../schemas
mixcr listPresets > ../presets/list.txt
