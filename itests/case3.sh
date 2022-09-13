#!/usr/bin/env bash

set -euxo pipefail

#mixcr analyze amplicon \
#      --receptor-type tra \
#      --impute-germline-on-export \
#      -s hs \
#      --starting-material rna \
#      --contig-assembly \
#      --5-end v-primers \
#      --3-end j-primers \
#      --adapters no-adapters \
#      test_R1.fastq test_R2.fastq case3

mixcr align --preset bcr_amplicon --verbose \
  +species hs \
  +rna \
  +floatingLeftAlignmentBoundary \
  +floatingRightAlignmentBoundary J \
  test_R1.fastq test_R2.fastq case3.vdjca
mixcr assemble -a case3.vdjca case3.clna
mixcr assembleContigs case3.clna case3.clns

# +rna +floatingLeftAlignmentBoundary L1Begin
# +dna +floatingLeftAlignmentBoundary L1Begin

# +floatingLeftAlignmentBoundary
# +floatingLeftAlignmentBoundary CDR1Begin
# +rigidLeftAlignmentBoundary
# +rigidLeftAlignmentBoundary CDR1Begin

# +floatingRightAlignmentBoundary C
# +floatingRightAlignmentBoundary CBegin(+21)
# +rigidRightAlignmentBoundary
# +rigidRightAlignmentBoundary J
# +rigidRightAlignmentBoundary FR4End(-20)
