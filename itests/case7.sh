#!/usr/bin/env bash

# Zero unaligned alignments

set -e

gzip -dc CD4M1_test_R1.fastq.gz | head -n 1012 | tail -n 4 >>case7_R1.fastq
gzip -dc CD4M1_test_R2.fastq.gz | head -n 1012 | tail -n 4 >>case7_R2.fastq

mixcr analyze tcr_amplicon \
  +species hs \
  +rna \
  +floatingLeftAlignmentBoundary \
  +floatingRightAlignmentBoundary C \
  +addStep assembleContigs \
  +imputeGermlineOnExport \
  case7_R1.fastq case7_R2.fastq case7
