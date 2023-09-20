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

mixcr analyze --verbose test-tcr-shotgun \
    --species hs \
    --rna \
    --add-step assembleContigs \
    --append-export-clones-field -nFeature '{FR1Begin:FR2End}' \
    --impute-germline-on-export \
    --append-export-clones-field -nFeature '{FR1Begin:FR3End}' \
    --floating-left-alignment-boundary \
    --floating-right-alignment-boundary C \
    --prepend-export-clones-field -geneLabel ReliableChain \
    test_R1.fastq test_R2.fastq case2

assert "head -n 1 case2.clones_TRB.tsv | grep -c nSeqImputed'{FR1Begin:FR2End}'" "1"
assert "head -n 1 case2.clones_TRB.tsv | grep -c nSeqImputed'{FR1Begin:FR3End}'" "1"

[[ -f case2.clna ]] || exit 1
