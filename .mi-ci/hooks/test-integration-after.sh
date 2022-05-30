#!/usr/bin/env bash

script_dir=$(dirname "${0}")

cd "${script_dir}/../.." || exit

#export MI_LICENSE_DEBUG=MI_LICENSE_DEBUG

# Run integration tests
./itests.sh test
