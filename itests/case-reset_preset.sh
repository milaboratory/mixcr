#!/usr/bin/env bash

set -euxo pipefail

mixcr align --preset test-tcr-shotgun \
    --species hs \
    --rna \
    --floating-left-alignment-boundary \
    --floating-right-alignment-boundary J\
    test_R1.fastq test_R2.fastq result.vdjca

mixcr exportPreset --mixcr-file result.vdjca > original.yaml

mixcr exportPreset --mixcr-file result.vdjca --reset-preset generic-tcr-amplicon \
  --species hs \
  --dna \
  --floating-left-alignment-boundary \
  --floating-right-alignment-boundary J > overridden.yaml

mixcr exportPreset --mixcr-file result.vdjca --reset-preset-keep-mixins generic-tcr-amplicon > overridden_with_same_mixins.yaml

mixcr assemble result.vdjca result.clns

mixcr assemble result.vdjca result_with_override.clns

#diff \
#  <(mixcr exportPreset --mixcr-file result.vdjca) \
#  <(mixcr exportPreset --mixcr-file result.vdjca --reset-preset-keep-mixins generic-tcr-amplicon)
