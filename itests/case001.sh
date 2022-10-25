#!/usr/bin/env bash

set -euxo pipefail

mixcr align -p legacy-4.0-default --species hs \
            --dna \
            -OsaveOriginalReads=true \
            test_R1.fastq test_R2.fastq case1.vdjca
mixcr exportAlignments case1.vdjca case1.vdjca.tsv
mixcr exportAirr --imgt-gaps case1.vdjca case1.vdjca.imgt.airr.tsv
mixcr exportAirr case1.vdjca case1.vdjca.airr.tsv
mixcr assemble case1.vdjca case1.clns
mixcr exportClones case1.clns case1.clns.tsv
mixcr exportAirr --imgt-gaps case1.clns case1.clns.imgt.airr.tsv
mixcr exportAirr case1.clns case1.clns.airr.tsv
mixcr exportReports case1.clns
mixcr exportReports --yaml case1.clns
