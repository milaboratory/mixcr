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

${dir}/export-meta.sh
${dir}/../ensure-test-data.sh
${dir}/itests.sh
