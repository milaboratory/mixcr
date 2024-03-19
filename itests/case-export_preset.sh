#!/usr/bin/env bash

set -euxo pipefail

mixcr analyze --verbose \
    --assemble-clonotypes-by CDR3 \
    --species hs \
    --rna \
    --floating-left-alignment-boundary \
    --floating-right-alignment-boundary J\
    test-tcr-shotgun test_R1.fastq test_R2.fastq result

mixcr exportPreset --preset-name test-tcr-shotgun \
    --assemble-clonotypes-by CDR3 \
    --species hs \
    --rna \
    --floating-left-alignment-boundary \
    --floating-right-alignment-boundary J\
    original_from_param.yaml

mixcr exportPreset --mixcr-file result.clns original_from_file.yaml

if ! cmp original_from_param.yaml original_from_file.yaml; then
  diff original_from_param.yaml original_from_file.yaml
fi
