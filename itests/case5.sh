#!/usr/bin/env bash

set -euxo pipefail

gzip -dc CD4M1_test_R1.fastq.gz CD4M1_test_R1.fastq.gz | tr 'N' 'A' > case5_R1.fastq
gzip -dc CD4M1_test_R2.fastq.gz CD4M1_test_R2.fastq.gz | tr 'N' 'A' > case5_R2.fastq

mixcr analyze amplicon --assemble '-OseparateByC=true' --assemble '-OseparateByV=true' --assemble '-OseparateByJ=true' --impute-germline-on-export -s hs --starting-material rna --contig-assembly --5-end v-primers --3-end j-primers --adapters adapters-present case5_R1.fastq case5_R2.fastq case5
#mixcr analyze amplicon --assemble '-OcloneClusteringParameters=null' --impute-germline-on-export -s hs --starting-material rna --contig-assembly --5-end v-primers --3-end j-primers --adapters adapters-present case5_R1.fastq case5_R2.fastq case5

mixcr exportAlignments -f -readIds -cloneIdWithMappingType case5.clna case5.als.txt

sort --field-separator='\t' --key=1 -n case5.als.txt | tail -n +2 > case5.als.sorted.txt

lines=$(cat case5.als.sorted.txt | wc -l)

head -n $((lines/2)) case5.als.sorted.txt | cut -f2 > case5.als.sorted.1.txt
tail -n $((lines/2)) case5.als.sorted.txt | cut -f2 > case5.als.sorted.2.txt

if cmp case5.als.sorted.1.txt case5.als.sorted.2.txt; then
  echo "All good"
else
  echo "Results are different"
  diff case5.als.sorted.1.txt case5.als.sorted.2.txt
fi
