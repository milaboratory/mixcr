#!/usr/bin/env bash

#
# Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
#
# Before downloading or accessing the software, please read carefully the
# License Agreement available at:
# https://github.com/milaboratory/mixcr/blob/develop/LICENSE
#
# By downloading or accessing the software, you accept and agree to be bound
# by the terms of the License Agreement. If you do not want to agree to the terms
# of the Licensing Agreement, you must not download or access the software.
#
# Start from BAM integration test

assert() {
  expected=$(echo -ne "${2:-}")
  result="$(eval 2>/dev/null $1)" || true
  result="$(sed -e 's/ *$//' -e 's/^ *//' <<<"$result")"
  if [[ "$result" == "$expected" ]]; then
    return
  fi
  result="$(sed -e :a -e '$!N;s/\n/\\n/;ta' <<<"$result")"
  [[ -z "$result" ]] && result="nothing" || result="\"$result\""
  [[ -z "$2" ]] && expected="nothing" || expected="\"$2\""
  echo "expected $expected got $result for" "$1"
  exit 1
}

mixcr align -f --preset test-generic -s hs --rna \
  --tag-pattern "^(R1:*)\^(R2:*)" \
  --floating-left-alignment-boundary \
  --rigid-right-alignment-boundary C \
  unpaired.bam bam.vdjca 2>err

cat err
assert "grep -c 'Tag pattern require BAM file to contain only paired reads' err" "1"

mixcr align -f --preset test-generic -s hs --rna \
  --tag-pattern "^(R1:*)" \
  --floating-left-alignment-boundary \
  --rigid-right-alignment-boundary C \
  unpaired.bam bam.vdjca 2>err

cat err
assert "grep -c 'Tag pattern require BAM file to contain only single reads' err" "1"

mixcr analyze generic-lt-single-cell-amplicon \
    --tag-pattern "^(R1:*)\^(R2:*)\^(CELL1:*)\^(CELL2:*)" \
    --species hsa \
    --rna \
    --floating-left-alignment-boundary \
    --floating-right-alignment-boundary C \
    --assemble-clonotypes-by CDR3 \
    subset_B004-7_S247_L001_R1_001.fastq.gz \
    subset_B004-7_S247_L001_R2_001.fastq.gz \
    subset_B004-7_S247_L001_I1_001.fastq.gz \
    output 2>err

cat err
assert "grep -c 'Tag pattern require 4 input files, got 3' err" "1"

mixcr analyze generic-lt-single-cell-amplicon \
    --tag-pattern "^(R1:*)\^(R2:*)\^(CELL1:*)\^(CELL2:*)" \
    --species hsa \
    --rna \
    --floating-left-alignment-boundary \
    --floating-right-alignment-boundary C \
    --assemble-clonotypes-by CDR3 \
    subset_B004-7_S247_L001_R1_001.fastq.gz \
    subset_B004-7_S247_L001_R2_001.fastq.gz \
    output 2>err

cat err
assert "grep -c 'Tag pattern require 4 input files, got 2' err" "1"

mixcr analyze generic-lt-single-cell-amplicon \
    --tag-pattern "^(R1:*)\^(R2:*)\^(CELL1:*)" \
    --species hsa \
    --rna \
    --floating-left-alignment-boundary \
    --floating-right-alignment-boundary C \
    --assemble-clonotypes-by CDR3 \
    subset_B004-7_S247_L001_R1_001.fastq.gz \
    subset_B004-7_S247_L001_R2_001.fastq.gz \
    output 2>err

cat err
assert "grep -c 'Tag pattern require 3 input files, got 2' err" "1"

mixcr analyze generic-amplicon \
    --tag-pattern "^(R1:*)\^(R2:*)" \
    --species hsa \
    --rna \
    --floating-left-alignment-boundary \
    --floating-right-alignment-boundary C \
    --assemble-clonotypes-by CDR3 \
    subset_B004-7_S247_L001_R1_001.fastq.gz \
    output 2>err

cat err
assert "grep -c 'Tag pattern require 2 input files, got 1' err" "1"

mixcr analyze generic-amplicon \
    --tag-pattern "^(R1:*)" \
    --species hsa \
    --rna \
    --floating-left-alignment-boundary \
    --floating-right-alignment-boundary C \
    --assemble-clonotypes-by CDR3 \
    subset_B004-7_S247_L001_R1_001.fastq.gz \
    subset_B004-7_S247_L001_R2_001.fastq.gz \
    output 2>err

cat err
assert "grep -c 'Tag pattern require 1 input file, got 2' err" "1"
