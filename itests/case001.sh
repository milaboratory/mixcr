#!/usr/bin/env bash

set -euxo pipefail

# +assembleClonotypesBy ShortCDR3 \

mixcr align -p default_4.0 -s hs \
            +dna \
            -OsaveOriginalReads=true \
            test_R1.fastq test_R2.fastq case1.vdjca
mixcr exportAlignments case1.vdjca case1.vdjca.txt
mixcr exportAirr --imgt-gaps case1.vdjca case1.vdjca.imgt.airr.tsv
mixcr exportAirr case1.vdjca case1.vdjca.airr.tsv
mixcr assemble case1.vdjca case1.clns
mixcr exportClones case1.clns case1.clns.txt
mixcr exportAirr --imgt-gaps case1.clns case1.clns.imgt.airr.tsv
mixcr exportAirr case1.clns case1.clns.airr.tsv
mixcr exportReports case1.clns
mixcr exportReports --yaml case1.clns
