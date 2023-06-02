#!/usr/bin/env bash

# Sample barcode integration test

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

set -euxo pipefail

ln -s single_cell_vdj_t_subset_R1.fastq.gz S1_single_cell_vdj_t_subset_R1.fastq.gz
ln -s single_cell_vdj_t_subset_R2.fastq.gz S1_single_cell_vdj_t_subset_R2.fastq.gz

ln -s single_cell_vdj_t_subset_R1.fastq.gz S2_single_cell_vdj_t_subset_R1.fastq.gz
ln -s single_cell_vdj_t_subset_R2.fastq.gz S2_single_cell_vdj_t_subset_R2.fastq.gz

mixcr analyze --verbose --verbose generic-tcr-amplicon-separate-samples-umi \
  --species hs \
  --rna \
  --rigid-left-alignment-boundary \
  --rigid-right-alignment-boundary C \
  --tag-pattern '^N{16}(UMI:N{10}) \ ^(R2:*)' \
  --infer-sample-table \
  '{{SAMPLE:a}}_single_cell_vdj_t_subset_{{R}}.fastq.gz' \
  case025-sample-barcode-from-file
