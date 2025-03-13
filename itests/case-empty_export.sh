#!/usr/bin/env bash

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

mixcr analyze generic-amplicon --species hs \
      --assemble-clonotypes-by CDR3 \
      --dna \
      --floating-left-alignment-boundary \
      --rigid-right-alignment-boundary C \
      test_R1.fastq test_R2.fastq caseee

[[ -f caseee.clns ]] || exit 1

mixcr exportClones --dont-split-files --chains IGH caseee.clns caseee_empty.tsv

[[ -f caseee_empty.tsv ]] || exit 1