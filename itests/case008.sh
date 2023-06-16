#!/usr/bin/env bash

# Regression Test #1

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

set -e

mixcr analyze --verbose generic-amplicon \
  --species hs \
  --rna \
  --floating-left-alignment-boundary \
  --floating-right-alignment-boundary C \
  --add-step assembleContigs \
  --split-clones-by V --split-clones-by J --split-clones-by C \
  -Malign.parameters.readsLayout=Collinear \
  CD4M1_test_R1.fastq.gz CD4M1_test_R2.fastq.gz case8

assert "cat case8.align.report.json | head -n 1 | jq -r .chainUsage.chains.TRA.total" "241381"
assert "cat case8.assemble.report.json | head -n 1 | jq -r .readsInClones" "200754"
assert "cat case8.assemble.report.json | head -n 1 | jq -r .clones" "25661"
assert "cat case8.assembleContigs.report.json | head -n 1 | jq -r .longestContigLength" "558"
assert "cat case8.assembleContigs.report.json | head -n 1 | jq -r .clonesWithAmbiguousLetters" "4521"
assert "cat case8.assembleContigs.report.json | head -n 1 | jq -r .assemblePrematureTerminationEvents" "4"
assert "cat case8.assembleContigs.report.json | head -n 1 | jq -r .finalCloneCount" "25661"
