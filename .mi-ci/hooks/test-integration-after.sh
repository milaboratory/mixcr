#!/usr/bin/env bash

script_dir=$(dirname "${0}")

# Run integration tests
"${script_dir}/../../itests.sh"
