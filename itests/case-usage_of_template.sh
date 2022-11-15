#!/usr/bin/env bash

set -euxo pipefail

mixcr analyze generic-tcr-amplicon \
  --species hs \
  --rna \
  --floating-left-alignment-boundary \
  --floating-right-alignment-boundary J \
  --add-step assembleContigs \
  test_{{R}}.fastq use_of_templates_1

[[ -f use_of_templates_1.contigs.clns ]] || exit 1

mixcr align -p generic-tcr-amplicon \
  --species hs \
  --rna \
  --floating-left-alignment-boundary \
  --floating-right-alignment-boundary J \
  test_{{R}}.fastq use_of_templates_2.vdjca

[[ -f use_of_templates_2.vdjca ]] || exit 1
