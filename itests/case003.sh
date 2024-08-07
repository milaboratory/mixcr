#!/usr/bin/env bash

set -euxo pipefail

mixcr analyze --verbose generic-amplicon --dry-run \
  --assemble-clonotypes-by CDR3 \
  --species hs \
  --rna \
  --floating-left-alignment-boundary \
  --floating-right-alignment-boundary J \
  --add-step assembleContigs \
  test_R1.fastq test_R2.fastq case3

mixcr analyze --verbose generic-amplicon \
  --assemble-clonotypes-by CDR3 \
  --species hs \
  --rna \
  --floating-left-alignment-boundary \
  --floating-right-alignment-boundary J \
  --add-step assembleContigs \
  test_R1.fastq test_R2.fastq case3

[[ -f case3.clna ]] || exit 1
