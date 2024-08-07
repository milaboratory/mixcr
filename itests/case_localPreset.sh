#!/usr/bin/env bash

set -euxo pipefail

mixcr exportPreset --preset-name generic-amplicon \
  --species hs \
  --rna \
  --floating-left-alignment-boundary \
  --floating-right-alignment-boundary J \
  --add-step assembleContigs \
  --assemble-clonotypes-by CDR3 \
  preset.yaml

mixcr analyze --verbose local:preset test_R1.fastq test_R2.fastq with_dot
[[ -f with_dot.clna ]] || exit 1

mixcr analyze local#preset test_R1.fastq test_R2.fastq with_sharp
[[ -f with_sharp.clna ]] || exit 1
