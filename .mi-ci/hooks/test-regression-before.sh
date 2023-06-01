#!/usr/bin/env bash

script_dir=$(dirname "${0}")

cd "${script_dir}/../.." || exit

#export MI_LICENSE_DEBUG=MI_LICENSE_DEBUG

# Downloadable test data
./ensure-test-data.sh

# Builds pre-processed test data
./prepare-test-data.sh reg
