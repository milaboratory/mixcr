#!/usr/bin/env bash

set -euxo pipefail

mixcr analyze generic-tcr-amplicon --dry-run \
  --species hs \
  --rna \
  --floating-left-alignment-boundary \
  --floating-right-alignment-boundary J \
  --add-step assembleContigs \
  test_R1.fastq test_R2.fastq case3_1

mixcr analyze generic-tcr-amplicon \
  --species hs \
  --rna \
  --floating-left-alignment-boundary \
  --floating-right-alignment-boundary J \
  --add-step assembleContigs \
  test_R1.fastq test_R2.fastq case3_1

[[ -f case3_1.clna ]] || exit 1

