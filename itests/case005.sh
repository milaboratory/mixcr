#!/usr/bin/env bash

set -euxo pipefail

gzip -dc CD4M1_test_R1.fastq.gz CD4M1_test_R1.fastq.gz | tr 'N' 'A' > case5_R1.fastq
gzip -dc CD4M1_test_R2.fastq.gz CD4M1_test_R2.fastq.gz | tr 'N' 'A' > case5_R2.fastq

#mixcr analyze amplicon --assemble '-OseparateByC=true' --assemble '-OseparateByV=true' --assemble '-OseparateByJ=true' --impute-germline-on-export -s hs --starting-material rna --contig-assembly --5-end v-primers --3-end j-primers --adapters adapters-present case5_R1.fastq case5_R2.fastq case5
#mixcr analyze amplicon --assemble '-OcloneClusteringParameters=null' --impute-germline-on-export -s hs --starting-material rna --contig-assembly --5-end v-primers --3-end j-primers --adapters adapters-present case5_R1.fastq case5_R2.fastq case5

mixcr analyze generic-tcr-amplicon \
  --species hs \
  --rna \
  --floating-left-alignment-boundary \
  --floating-right-alignment-boundary J \
  --add-step assembleContigs \
  --split-clones-by V --split-clones-by J --split-clones-by C \
  case5_R1.fastq case5_R2.fastq case5

mixcr exportAlignments -f --drop-default-fields -readIds -cloneIdWithMappingType case5.clna case5.als.tsv

sort -t $'\t' --key=1 -n case5.als.tsv | tail -n +2 > case5.als.sorted.tsv

lines=$(cat case5.als.sorted.tsv | wc -l)

head -n $((lines/2)) case5.als.sorted.tsv | cut -f2 > case5.als.sorted.1.tsv
tail -n $((lines/2)) case5.als.sorted.tsv | cut -f2 > case5.als.sorted.2.tsv

if cmp case5.als.sorted.1.tsv case5.als.sorted.2.tsv; then
  echo "All good"
else
  echo "Results are different"
  diff case5.als.sorted.1.tsv case5.als.sorted.2.tsv
fi
