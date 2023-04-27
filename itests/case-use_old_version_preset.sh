#!/usr/bin/env bash

set -euxo pipefail

mixcr analyze generic-tcr-amplicon-legacy-v4.2.0 \
  --species hs \
  --rna \
  --floating-left-alignment-boundary \
  --floating-right-alignment-boundary J \
  --add-step assembleContigs \
  test_R1.fastq test_R2.fastq old_version

[[ -f old_version.clna ]] || exit 1
