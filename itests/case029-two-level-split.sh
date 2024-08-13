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

cp single_cell_vdj_t_subset_R1.fastq.gz single_cell_vdj_t_subset_1_R1.fastq.gz
cp single_cell_vdj_t_subset_R2.fastq.gz single_cell_vdj_t_subset_1_R2.fastq.gz
cp single_cell_vdj_t_subset_R1.fastq.gz single_cell_vdj_t_subset_2_R1.fastq.gz
cp single_cell_vdj_t_subset_R2.fastq.gz single_cell_vdj_t_subset_2_R2.fastq.gz


echo -e 'Sample\tTagPattern\tCELL*' > 10x-samplesheet.tsv
echo -e 'A\t\tGGATTACTCATTGCCC' >> 10x-samplesheet.tsv
echo -e 'A\t\tCTGAAGTTCAAGGTAA' >> 10x-samplesheet.tsv
echo -e 'B\t\tAACTCCCAGATCCTGT' >> 10x-samplesheet.tsv

mixcr analyze 10x-sc-xcr-vdj \
  --species hs \
  --sample-sheet 10x-samplesheet.tsv \
  single_cell_vdj_t_subset_{{SREPLICA}}_{{R}}.fastq.gz \
  output_with_two_level_split/

[[ -f output_with_two_level_split/1.A.clones.tsv ]] || exit 1
[[ -f output_with_two_level_split/1.B.clones.tsv ]] || exit 1
[[ -f output_with_two_level_split/2.A.clones.tsv ]] || exit 1
[[ -f output_with_two_level_split/2.B.clones.tsv ]] || exit 1

