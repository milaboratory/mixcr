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

function find_zip_archive() {
    local zip_files
    IFS=$'\n' zip_files=( $(find "${ARCHIVE_PATH}" -type f -name '*.zip') )

    if [ ${#zip_files[@]} -eq 0 ]; then
        log "No zip archive found in '${ARCHIVE_PATH}'"
        return 1
    elif [ ${#zip_files[@]} -gt 1 ]; then
        log "More than one zip archive found. Please specify which one to use."
        return 1
    else
        ARCHIVE_PATH="${zip_files[0]}"
        return 0
    fi
}

if ! find_zip_archive; then
    exit 1
fi

log "Zip archive found at '${ARCHIVE_PATH}'"
log "Creating: ${dst_data_dir}"

rm -rf "${dst_data_dir}" # make sure we have no waste to be packed into software package
mkdir -p "${dst_data_dir}"

unzip "${ARCHIVE_PATH}" -d "${dst_data_dir}"
