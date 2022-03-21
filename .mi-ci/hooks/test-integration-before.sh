#!/usr/bin/env bash

script_dir=$(dirname "${0}")

# downloadable test data
"${script_dir}/../../ensure-test-data.sh"

# builds predprocessed test data
"${script_dir}/../../prepare-test-data.sh"
