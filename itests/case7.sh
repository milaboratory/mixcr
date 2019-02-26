#!/usr/bin/env bash

# Zero unaligned alignments

set -e

gzip -dc CD4M1_test_R1.fastq.gz | head -n 1012 | tail -n 4 >> case7_R1.fastq
gzip -dc CD4M1_test_R2.fastq.gz | head -n 1012 | tail -n 4 >> case7_R2.fastq

mixcr analyze amplicon --receptor-type tra --impute-germline-on-export -s hs \
      --starting-material rna --contig-assembly --5-end v-primers --3-end j-primers \
      --adapters no-adapters case7_R1.fastq case7_R2.fastq case7
