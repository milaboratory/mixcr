#!/usr/bin/env bash

set -euxo pipefail

mixcr analyze amplicon --receptor-type tra --impute-germline-on-export -s hs --starting-material rna --contig-assembly --5-end v-primers --3-end j-primers --adapters no-adapters test_R1.fastq test_R2.fastq case3