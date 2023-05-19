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

echo -e 'Sample\tCELL\tTagPattern\tR1\tR2' > sample_sheet_1.tsv
echo -e 'S1\tGGATTACTCATTGCCC\t^(CELL:N{16})(UMI:N{10})\\^(R2:*)\tsingle_cell_vdj_t_subset_R1.fastq.gz\tsingle_cell_vdj_t_subset_R2.fastq.gz' >> sample_sheet_1.tsv
echo -e 'S2\tCTGAAGTTCAAGGTAA\t^(CELL:N{16})(UMI:N{10})\\^(R2:*)\tsingle_cell_vdj_t_subset_R1.fastq.gz\tsingle_cell_vdj_t_subset_R2.fastq.gz' >> sample_sheet_1.tsv
echo -e 'S3\tAACTCCCAGATCCTGT\t^(CELL:N{16})(UMI:N{10})\\^(R2:*)\tsingle_cell_vdj_t_subset_R1.fastq.gz\tsingle_cell_vdj_t_subset_R2.fastq.gz' >> sample_sheet_1.tsv

mixcr analyze generic-tcr-amplicon-separate-samples-umi \
  --species hs \
  --rna \
  --rigid-left-alignment-boundary \
  --rigid-right-alignment-boundary C \
  sample_sheet_1.tsv \
  case026-sample-barcode-split_1

cp single_cell_vdj_t_subset_R1.fastq.gz copy_single_cell_vdj_t_subset_R1.fastq.gz
cp single_cell_vdj_t_subset_R2.fastq.gz copy_single_cell_vdj_t_subset_R2.fastq.gz

echo -e 'Sample\tCELL\tTagPattern\tR1\tR2' > sample_sheet_2.tsv
echo -e 'S1\tGGATTACTCATTGCCC\t^(CELL:N{16})(UMI:N{10})\\^(R2:*)\tsingle_cell_vdj_t_subset_R1.fastq.gz\tsingle_cell_vdj_t_subset_R2.fastq.gz' >> sample_sheet_2.tsv
echo -e 'S2\tCTGAAGTTCAAGGTAA\t^(CELL:N{16})(UMI:N{10})\\^(R2:*)\tsingle_cell_vdj_t_subset_R1.fastq.gz\tsingle_cell_vdj_t_subset_R2.fastq.gz' >> sample_sheet_2.tsv
echo -e 'S3\tAACTCCCAGATCCTGT\t^(CELL:N{16})(UMI:N{10})\\^(R2:*)\tsingle_cell_vdj_t_subset_R1.fastq.gz\tsingle_cell_vdj_t_subset_R2.fastq.gz' >> sample_sheet_2.tsv
echo -e 'S4\tGGATTACTCATTGCCC\t^(CELL:N{16})(UMI:N{10})\\^(R2:*)\tcopy_single_cell_vdj_t_subset_R1.fastq.gz\tcopy_single_cell_vdj_t_subset_R2.fastq.gz' >> sample_sheet_2.tsv
echo -e 'S5\tCTGAAGTTCAAGGTAA\t^(CELL:N{16})(UMI:N{10})\\^(R2:*)\tcopy_single_cell_vdj_t_subset_R1.fastq.gz\tcopy_single_cell_vdj_t_subset_R2.fastq.gz' >> sample_sheet_2.tsv
echo -e 'S6\tAACTCCCAGATCCTGT\t^(CELL:N{16})(UMI:N{10})\\^(R2:*)\tcopy_single_cell_vdj_t_subset_R1.fastq.gz\tcopy_single_cell_vdj_t_subset_R2.fastq.gz' >> sample_sheet_2.tsv

mixcr analyze generic-tcr-amplicon-separate-samples-umi \
  --species hs \
  --rna \
  --rigid-left-alignment-boundary \
  --rigid-right-alignment-boundary C \
  sample_sheet_2.tsv \
  case026-sample-barcode-split_2

for s in 1 2 3; do
  for chain in TRAD TRB; do
    cmp "case026-sample-barcode-split_1.S${s}.clones_${chain}.tsv" "case026-sample-barcode-split_2.S${s}.clones_${chain}.tsv"
    cmp "case026-sample-barcode-split_2.S${s}.clones_${chain}.tsv" "case026-sample-barcode-split_2.S$((s + 3)).clones_${chain}.tsv"
  done
done
