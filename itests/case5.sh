#!/usr/bin/env bash

set -euxo pipefail

mixcr analyze amplicon --receptor-type tra --assemble '-OseparateByC=true' --assemble '-OseparateByV=true' --assemble '-OseparateByJ=true' --impute-germline-on-export -s hs --starting-material rna --contig-assembly --5-end v-primers --3-end j-primers --adapters adapters-present CD4M1_test_R1.fastq.gz CD4M1_test_R2.fastq.gz case5
