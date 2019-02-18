#!/usr/bin/env bash

set -euxo pipefail

touch empty_R1.fastq
touch empty_R2.fastq
mixcr analyze amplicon --receptor-type tra --impute-germline-on-export -s hs --starting-material rna --contig-assembly --5-end v-primers --3-end j-primers --adapters no-adapters empty_R1.fastq empty_R2.fastq case6