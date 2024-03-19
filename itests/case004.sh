#!/usr/bin/env bash

set -euxo pipefail

# Checking generic pipeline with relatively big input files
mixcr analyze --verbose generic-amplicon \
  --assemble-clonotypes-by CDR3 \
  --species hs \
  --rna \
  --floating-left-alignment-boundary \
  --floating-right-alignment-boundary J \
  --add-step assembleContigs \
  CD4M1_test_R1.fastq.gz CD4M1_test_R2.fastq.gz case4

# Checking AIRR export on big files
mixcr exportAirr --imgt-gaps case4.vdjca case4.vdjca.imgt.airr.tsv
mixcr exportAirr --imgt-gaps --from-alignment case4.vdjca case4.vdjca.imgta.airr.tsv
mixcr exportAirr case4.vdjca case4.vdjca.airr.tsv

mixcr exportAirr --imgt-gaps case4.clna case4.clna.imgt.airr.tsv
mixcr exportAirr --imgt-gaps --from-alignment case4.clna case4.clna.imgta.airr.tsv
mixcr exportAirr case4.clna case4.clna.airr.tsv
