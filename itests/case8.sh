#!/usr/bin/env bash

# Regression Test #1

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

mixcr analyze amplicon \
  -s hs --starting-material rna --contig-assembly --5-end v-primers --3-end j-primers --adapters adapters-present \
  --assemble '-OseparateByC=true' --assemble '-OseparateByV=true' --assemble '-OseparateByJ=true' \
  --impute-germline-on-export --json-report case8 \
  CD4M1_test_R1.fastq.gz CD4M1_test_R2.fastq.gz case8

assert "cat case8.align.jsonl | head -n 1 | jq -r .chainUsage.chains.TRA" "197562"
assert "cat case8.assemble.jsonl | head -n 1 | jq -r .readsInClones" "161627"
assert "cat case8.assembleContigs.jsonl | head -n 1 | jq -r .longestContigLength" "223"
assert "cat case8.assembleContigs.jsonl | head -n 1 | jq -r .clonesWithAmbiguousLetters" "1378"
assert "cat case8.assembleContigs.jsonl | head -n 1 | jq -r .assemblePrematureTerminationEvents" "1"
assert "cat case8.assembleContigs.jsonl | head -n 1 | jq -r .finalCloneCount" "22402"
