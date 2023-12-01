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

#      splitBySample: true
#      tagPattern: ^(SMPL:N{16})(UMI:N{10})\^(R2:*)
#      sampleTable:
#        sampleTagName: SAMPLE
#        samples:
#          - matchTags:
#              SMPL: "GGATTACTCATTGCCC"
#            sampleName: sample0
#          - matchTags:
#              SMPL: "CTGAAGTTCAAGGTAA"
#            sampleName: sample1
#          - matchTags:
#              SMPL: "AACTCCCAGATCCTGT"
#            sampleName: sample2

echo -e 'Sample\tTagPattern' > sample_table_1.tsv
echo -e 'S1\t^GGATTACTCATTGCCC(UMI:N{10}) \ ^(R2:*)' >> sample_table_1.tsv
echo -e 'S2\t^CTGAAGTTCAAGGTAA(UMI:N{10}) \ ^(R2:*)' >> sample_table_1.tsv
echo -e 'S3\t^AACTCCCAGATCCTGT(UMI:N{10}) \ ^(R2:*)' >> sample_table_1.tsv

mixcr analyze --verbose generic-tcr-amplicon-separate-samples-umi \
  --species hs \
  --rna \
  --rigid-left-alignment-boundary \
  --rigid-right-alignment-boundary C \
  --sample-table-strict sample_table_1.tsv \
  single_cell_vdj_t_subset_R1.fastq.gz \
  single_cell_vdj_t_subset_R2.fastq.gz \
  case024-sample-barcode-split_1

echo -e 'Sample\tTagPattern\tSPL' > sample_table_2.tsv
echo -e 'S1\t^(SPL:N{16})(UMI:N{10}) \ ^(R2:*)\tGGATTACTCATTGCCC' >> sample_table_2.tsv
echo -e 'S2\t^(SPL:N{16})(UMI:N{10}) \ ^(R2:*)\tCTGAAGTTCAAGGTAA' >> sample_table_2.tsv
echo -e 'S3\t^(SPL:N{16})(UMI:N{10}) \ ^(R2:*)\tAACTCCCAGATCCTGT' >> sample_table_2.tsv

mixcr analyze --verbose generic-tcr-amplicon-separate-samples-umi \
  --species hs \
  --rna \
  --rigid-left-alignment-boundary \
  --rigid-right-alignment-boundary C \
  --sample-table-strict sample_table_2.tsv \
  single_cell_vdj_t_subset_R1.fastq.gz \
  single_cell_vdj_t_subset_R2.fastq.gz \
  case024-sample-barcode-split_2

echo -e 'Sample\tTagPattern\tSPL' > sample_table_3.tsv
echo -e 'S1\t\tGGATTACTCATTGCCC' >> sample_table_3.tsv
echo -e 'S2\t\tCTGAAGTTCAAGGTAA' >> sample_table_3.tsv
echo -e 'S3\t\tAACTCCCAGATCCTGT' >> sample_table_3.tsv

mixcr analyze --verbose generic-tcr-amplicon-separate-samples-umi \
  --tag-pattern '^(SPL:N{16})(UMI:N{10}) \ ^(R2:*)' \
  --species hs \
  --rna \
  --rigid-left-alignment-boundary \
  --rigid-right-alignment-boundary C \
  --sample-table-strict sample_table_3.tsv \
  single_cell_vdj_t_subset_R1.fastq.gz \
  single_cell_vdj_t_subset_R2.fastq.gz \
  case024-sample-barcode-split_3

for s in 1 2 3; do
  for chain in TRA TRB; do
    cmp "case024-sample-barcode-split_1.S${s}.clones_${chain}.tsv" "case024-sample-barcode-split_2.S${s}.clones_${chain}.tsv"
    cmp "case024-sample-barcode-split_2.S${s}.clones_${chain}.tsv" "case024-sample-barcode-split_3.S${s}.clones_${chain}.tsv"
  done
done
