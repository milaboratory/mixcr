#!/usr/bin/env bash

set -euxo pipefail

mixcr analyze --verbose test-tcr-shotgun \
    --species hs \
    --rna \
    --add-step assembleContigs \
    --impute-germline-on-export \
    --floating-left-alignment-boundary \
    --floating-right-alignment-boundary C \
    --prepend-export-clones-field -geneLabel ReliableChain \
    test_R1.fastq test_R2.fastq case2

[[ -f case2.clna ]] || exit 1
