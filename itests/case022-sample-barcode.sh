#!/usr/bin/env bash

# Sample barcode integration test

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

set -euxo pipefail

mixcr analyze single-cell-as-sample \
  --dont-split-by-sample \
  --species hs \
  single_cell_vdj_t_subset_R1.fastq.gz \
  single_cell_vdj_t_subset_R2.fastq.gz \
  sample-barcode
