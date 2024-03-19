#!/usr/bin/env bash

set -euxo pipefail

mixcr analyze --verbose generic-amplicon \
  --assemble-clonotypes-by CDR3 \
  --tag-pattern '^N(R1:*) \ ^N(R2:*)' \
  --species hs \
  --rna \
  --floating-left-alignment-boundary \
  --floating-right-alignment-boundary J \
  --add-step assembleContigs \
  test_R1.fastq test_R2.fastq case17

[[ -f case17.clna ]] || exit 1
