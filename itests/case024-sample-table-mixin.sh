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

echo -e 'Sample\tR1\tR2' > sample_table.tsv
echo -e 'S1\t^GGATTACTCATTGCCC(UMI:N{10})\t^(R2:*)' >> sample_table.tsv
echo -e 'S2\t^CTGAAGTTCAAGGTAA(UMI:N{10})\t^(R2:*)' >> sample_table.tsv
echo -e 'S3\t^AACTCCCAGATCCTGT(UMI:N{10})\t^(R2:*)' >> sample_table.tsv

mixcr analyze generic-tcr-amplicon-separate-samples-umi \
  --species hs \
  --rna \
  --rigid-left-alignment-boundary \
  --rigid-right-alignment-boundary C \
  --sample-table sample_table.tsv \
  single_cell_vdj_t_subset_R1.fastq.gz \
  single_cell_vdj_t_subset_R2.fastq.gz \
  case024-sample-barcode-split
