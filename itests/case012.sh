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
      umi_single_read_R1.fastq \
      umi_single_read_R2.fastq \
      case12-without-contigs

mixcr analyze test-mikelov-et-al-2021-with-contigs \
      umi_single_read_R1.fastq \
      umi_single_read_R2.fastq \
      case12-with-contigs

mixcr exportClones --drop-default-fields -nFeature VDJRegion case12-with-contigs.contigs.clns case12-with-contigs.clns.txt
mixcr exportClones --drop-default-fields -nFeature VDJRegion case12-without-contigs.clns case12-without-contigs.clns.txt

sort case12-with-contigs.clns.txt > case12-with-contigs.clns.txt.s
sort case12-without-contigs.clns.txt > case12-without-contigs.clns.txt.s

[[ $(cat case12-with-contigs.clns.txt.s | wc -l) -eq 2 ]] || exit 1
cmp case12-with-contigs.clns.txt.s case12-without-contigs.clns.txt.s
