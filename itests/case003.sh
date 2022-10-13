#!/usr/bin/env bash

set -euxo pipefail

mixcr analyze tcr_amplicon --dry-run \
  +species hs \
  +rna \
  +floatingLeftAlignmentBoundary \
  +floatingRightAlignmentBoundary J \
  +addStep assembleContigs \
  test_R1.fastq test_R2.fastq case3_1

mixcr analyze tcr_amplicon \
  +species hs \
  +rna \
  +floatingLeftAlignmentBoundary \
  +floatingRightAlignmentBoundary J \
  +addStep assembleContigs \
  test_R1.fastq test_R2.fastq case3_2

[[ -f case3_2.clna ]] || exit 1

