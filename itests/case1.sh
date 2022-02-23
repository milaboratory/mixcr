#!/usr/bin/env bash

set -euxo pipefail

mixcr align -s hs -OvParameters.geneFeatureToAlign=VGeneWithP -OsaveOriginalReads=true test_R1.fastq test_R2.fastq case1.vdjca
mixcr exportAirr case1.vdjca case1.vdjca.airr.tsv
mixcr assemble case1.vdjca case1.clns
mixcr exportAirr case1.clns case1.clns.airr.tsv
