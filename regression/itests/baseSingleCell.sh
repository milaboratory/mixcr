#!/usr/bin/env bash

set -euxo pipefail

mixcr analyze -f 10x-vdj-bcr \
  --species hs \
  single_cell_vdj_t_subset_R1.fastq.gz \
  single_cell_vdj_t_subset_R2.fastq.gz \
  baseSingleCell.raw

mixcr analyze -f 10x-vdj-bcr \
  --species hs \
  --assemble-contigs-by VDJRegion \
  single_cell_vdj_t_subset_R1.fastq.gz \
  single_cell_vdj_t_subset_R2.fastq.gz \
  baseSingleCell.vdjcontigs

mixcr exportReports --yaml baseSingleCell.raw.contigs.clns | sed '/version:/d' > ../reports/baseSingleCell.raw.yaml
mixcr exportReports --yaml baseSingleCell.vdjcontigs.contigs.clns | sed '/version:/d' > ../reports/baseSingleCell.vdjcontigs.yaml
