#!/usr/bin/env bash

set -euxo pipefail

! (mixcr analyze test-gf-intersection \
     --species hs \
     single_cell_vdj_t_subset_R1.fastq.gz \
     single_cell_vdj_t_subset_R2.fastq.gz \
     test-gf-intersection 2>&1 | grep VIntron)

