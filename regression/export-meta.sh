#!/usr/bin/env bash

dir=""

os=$(uname)

case $os in
Darwin)
  dir=$(dirname "$(readlinkUniversal "$0")")
  ;;
Linux)
  dir="$(dirname "$(readlink -f "$0")")"
  ;;
FreeBSD)
  dir=$(dirname "$(readlinkUniversal "$0")")
  ;;
*)
  echo "Unknown OS."
  exit 1
  ;;
esac

set -euxo pipefail

PATH=${dir}/..:${PATH}

mixcr exportHelp cli-help
mixcr exportAllPresets presets
mixcr exportSchemas schemas
