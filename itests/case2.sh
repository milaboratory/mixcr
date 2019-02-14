#!/usr/bin/env bash

set -euxo pipefail

mixcr analyze shotgun -f --species hs --contig-assembly --impute-germline-on-export --starting-material rna test_R1.fastq test_R2.fastq case2
