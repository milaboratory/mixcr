#!/usr/bin/env bash

# Single-cell integration test

assert() {
  expected=$(echo -ne "${2:-}")
  result="$(eval 2>/dev/null $1)" || true
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

mixcr analyze test-mikelov-et-al-2021-without-contigs \
      umi_ig_data_2_subset_R1.fastq.gz \
      umi_ig_data_2_subset_R2.fastq.gz \
      case11-without-contigs

mixcr analyze test-mikelov-et-al-2021-with-contigs \
      umi_ig_data_2_subset_R1.fastq.gz \
      umi_ig_data_2_subset_R2.fastq.gz \
      case11-with-contigs

mixcr exportClones --drop-default-fields -nFeature VDJRegion case11-with-contigs.contigs.clns case11-with-contigs.clns.tsv
mixcr exportClones --drop-default-fields -nFeature VDJRegion case11-without-contigs.clns case11-without-contigs.clns.tsv

sort case11-with-contigs.clns_IGH.tsv > case11-with-contigs.clns.tsv.s
sort case11-without-contigs.clns_IGH.tsv > case11-without-contigs.clns.tsv.s

[[ $(cat case11-with-contigs.clns.tsv.s | wc -l) -eq 4 ]] || exit 1
cmp case11-with-contigs.clns.tsv.s case11-without-contigs.clns.tsv.s
