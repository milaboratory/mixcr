#!/usr/bin/env bash

set -euxo pipefail

mixcr analyze generic-tcr-amplicon \
  --species hs \
  --rna \
  --floating-left-alignment-boundary \
  --floating-right-alignment-boundary J \
  --add-step assembleContigs \
  test_{{R}}.fastq result_1

[[ -f result_1.contigs.clns ]] || exit 1

mixcr align -p generic-tcr-amplicon \
  --species hs \
  --rna \
  --floating-left-alignment-boundary \
  --floating-right-alignment-boundary J \
  test_{{R}}.fastq result_2.vdjca

[[ -f result_2.vdjca ]] || exit 1
