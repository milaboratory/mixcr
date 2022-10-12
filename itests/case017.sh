#!/usr/bin/env bash

set -euxo pipefail

mixcr analyze tcr_amplicon \
  +tagPattern '^N(R1:*) \ ^N(R2:*)' \
  +species hs \
  +rna \
  +floatingLeftAlignmentBoundary \
  +floatingRightAlignmentBoundary J \
  +addStep assembleContigs \
  test_R1.fastq test_R2.fastq case17

[[ -f case17.clna ]] || exit 1
