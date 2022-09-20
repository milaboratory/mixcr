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

mixcr exportClones -nFeature VDJRegion case11-with-contigs.contigs.clns case11-with-contigs.clns.txt
mixcr exportClones -nFeature VDJRegion case11-without-contigs.clns case11-without-contigs.clns.txt

sort case11-with-contigs.clns.txt > case11-with-contigs.clns.txt.s
sort case11-without-contigs.clns.txt > case11-without-contigs.clns.txt.s

cmp case11-with-contigs.clns.txt.s case11-without-contigs.clns.txt.s
