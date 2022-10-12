#!/usr/bin/env bash

set -euxo pipefail

touch empty_R1.fastq
touch empty_R2.fastq

mixcr analyze tcr_amplicon \
  +species hs \
  +rna \
  +floatingLeftAlignmentBoundary \
  +floatingRightAlignmentBoundary J \
  +addStep assembleContigs \
  +imputeGermlineOnExport \
  empty_R1.fastq empty_R2.fastq case6

[[ -f case6.clna ]] || exit 1
