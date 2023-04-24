#!/usr/bin/env bash

# Zero unaligned alignments

set -e

gzip -dc CD4M1_test_R1.fastq.gz | head -n 1012 | tail -n 4 >>case7_R1.fastq
gzip -dc CD4M1_test_R2.fastq.gz | head -n 1012 | tail -n 4 >>case7_R2.fastq

mixcr analyze generic-amplicon \
  --species hs \
  --rna \
  --floating-left-alignment-boundary \
  --floating-right-alignment-boundary C \
  --add-step assembleContigs \
  --impute-germline-on-export \
  case7_R1.fastq case7_R2.fastq case7

[[ -f case7.clna ]] || exit 1
