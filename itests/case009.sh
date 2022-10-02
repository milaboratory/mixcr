#!/usr/bin/env bash

# Regression Test #2

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

set -e

mixcr analyze tcr_amplicon \
  +species hs \
  +rna \
  +floatingLeftAlignmentBoundary \
  +floatingRightAlignmentBoundary C \
  +addStep assembleContigs \
  +splitClonesBy V +splitClonesBy J +splitClonesBy C \
  +M assemble.sortBySequence=true \
  CD4M1_test_R1.fastq.gz CD4M1_test_R2.fastq.gz case9

assert "cat case9.align.report.json | head -n 1 | jq -r .chainUsage.chains.TRA.total" "237718"
assert "cat case9.assemble.report.json | head -n 1 | jq -r .readsInClones" "199560"
assert "cat case9.assembleContigs.report.json | head -n 1 | jq -r .longestContigLength" "227"
assert "cat case9.assembleContigs.report.json | head -n 1 | jq -r .clonesWithAmbiguousLetters" "769"
assert "cat case9.assembleContigs.report.json | head -n 1 | jq -r .assemblePrematureTerminationEvents" "3"
assert "cat case9.assembleContigs.report.json | head -n 1 | jq -r .finalCloneCount" "25761"
