#!/usr/bin/env bash

set -euxo pipefail

touch empty_R1.fastq
touch empty_R2.fastq

mixcr analyze tcr_amplicon \
  --species hs \
  --rna \
  --floating-left-alignment-boundary \
  --floating-right-alignment-boundary J \
  --add-step assembleContigs \
  --impute-germline-on-export \
  empty_R1.fastq empty_R2.fastq case6

[[ -f case6.clna ]] || exit 1
