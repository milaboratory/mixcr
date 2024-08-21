#!/usr/bin/env bash

set -o errexit
set -o nounset

if [ -z "${CI:-}" ]; then
    echo "Not a CI run. install-ci.sh was skipped"
    exit 0
fi

#
# Script settings
#
script_dir="$(cd "$(dirname "${0}")" && pwd)"
cd "${script_dir}"

#
# Script parameters
#
: "${ARCHIVE_PATH}" # require variable to be set
dst_root="${script_dir}/dld"
dst_data_dir="${dst_root}/mixcr"

function log() {
    printf "%s\n" "${*}"
}

if [ -z "$( ls -A ${ARCHIVE_PATH} )" ]; then
    log "Artifact folder with MiXCR is empty: '${ARCHIVE_PATH}'"
    exit 1
fi	

log "Creating: ${dst_data_dir}"

rm -rf "${dst_data_dir}" # make sure we have no waste to be packed into software package
mkdir -p "${dst_data_dir}"

cp -av "${ARCHIVE_PATH}"/* "${dst_data_dir}"/
