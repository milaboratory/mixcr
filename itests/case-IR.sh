#!/usr/bin/env bash

# Single-cell integration test

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

set -eux

mixcr analyze generic-lt-single-cell-amplicon \
    --assemble-clonotypes-by CDR3 \
    --tag-pattern "^(CELL1:*)\^(CELL2:*)\^(R1:*)\^(R2:*)" \
    --species hsa \
    --rna \
    --floating-left-alignment-boundary \
    --floating-right-alignment-boundary C \
    subset_B004-7_S247_L001_I1_001.fastq.gz \
    subset_B004-7_S247_L001_I2_001.fastq.gz \
    subset_B004-7_S247_L001_R1_001.fastq.gz \
    subset_B004-7_S247_L001_R2_001.fastq.gz \
    output_normal

mixcr analyze generic-lt-single-cell-amplicon \
    --assemble-clonotypes-by CDR3 \
    --tag-pattern "^(CELL1:*)\^(CELL2:*)\^(R1:*)\^(R2:*)" \
    --species hsa \
    --rna \
    --floating-left-alignment-boundary \
    --floating-right-alignment-boundary C \
    subset_B004-7_S247_L001_{{IR}}_001.fastq.gz \
    output_with_template

## R2 as UMI
mixcr analyze generic-lt-single-cell-amplicon-with-umi \
    --assemble-clonotypes-by CDR3 \
    --tag-pattern "^(CELL1:*)\^(CELL2:*)\^(R1:*)\^(UMI:*)" \
    --species hsa \
    --rna \
    --floating-left-alignment-boundary \
    --floating-right-alignment-boundary C \
    subset_B004-7_S247_L001_I1_001.fastq.gz \
    subset_B004-7_S247_L001_I2_001.fastq.gz \
    subset_B004-7_S247_L001_R1_001.fastq.gz \
    subset_B004-7_S247_L001_R2_001.fastq.gz \
    output_UMI1

# R1 as UMI and payload
mixcr analyze generic-lt-single-cell-amplicon-with-umi \
    --assemble-clonotypes-by CDR3 \
    --tag-pattern "^(CELL1:*)\^(CELL2:*)\^N{16}(UMI:N{10})(R1:*)\^(R2:*)" \
    --species hsa \
    --rna \
    --floating-left-alignment-boundary \
    --floating-right-alignment-boundary C \
    subset_B004-7_S247_L001_I1_001.fastq.gz \
    subset_B004-7_S247_L001_I2_001.fastq.gz \
    subset_B004-7_S247_L001_R1_001.fastq.gz \
    subset_B004-7_S247_L001_R2_001.fastq.gz \
    output_UMI2

# R1+R2+I1
mixcr analyze generic-lt-single-cell-amplicon \
    --assemble-clonotypes-by CDR3 \
    --tag-pattern "^(CELL1:*)\^(R1:*)\^(R2:*)" \
    --species hsa \
    --rna \
    --floating-left-alignment-boundary \
    --floating-right-alignment-boundary C \
    subset_B004-7_S247_L001_I1_001.fastq.gz \
    subset_B004-7_S247_L001_R1_001.fastq.gz \
    subset_B004-7_S247_L001_R2_001.fastq.gz \
    output_R1_R2_I1
