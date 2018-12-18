#!/usr/bin/env bash

set -euxo pipefail

mixcr align -s hs -OvParameters.geneFeatureToAlign=VGeneWithP -OsaveOriginalReads=true test_R1.fastq test_R2.fastq case1.vdjca
mixcr assemble case1.vdjca case1.clns
