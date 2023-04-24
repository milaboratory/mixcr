#!/usr/bin/env bash

set -euxo pipefail

mixcr analyze exome-full-length \
    --species hs \
    single_cell_vdj_t_subset_R1.fastq.gz single_cell_vdj_t_subset_R2.fastq.gz case023

[[ -f case023.clna ]] || exit 1
