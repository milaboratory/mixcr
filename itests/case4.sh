#!/usr/bin/env bash

set -euxo pipefail

# Checking generic pipeline with relatively big input files
mixcr analyze amplicon --receptor-type tra --impute-germline-on-export -s hs --starting-material rna --contig-assembly --5-end v-primers --3-end j-primers --adapters adapters-present CD4M1_test_R1.fastq.gz CD4M1_test_R2.fastq.gz case4

# Checking skip steps behaviour
mixcr analyze amplicon --receptor-type tra --impute-germline-on-export -s hs --starting-material rna --contig-assembly --5-end v-primers --3-end j-primers --adapters adapters-present CD4M1_test_R1.fastq.gz CD4M1_test_R2.fastq.gz case4
