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

mixcr analyze amplicon \
  -s hs --starting-material rna --contig-assembly --5-end v-primers --3-end j-primers --adapters adapters-present \
  --assemble '-OseparateByC=true' --assemble '-OseparateByV=true' --assemble '-OseparateByJ=true' \
  --assemble '--sort-by-sequence' \
  --impute-germline-on-export --json-report case9 \
  CD4M1_test_R1.fastq.gz CD4M1_test_R2.fastq.gz case9

assert "cat case9.align.jsonl | head -n 1 | jq -r .chainUsage.chains.TRAD" "198684"
assert "cat case9.assemble.jsonl | head -n 1 | jq -r .readsInClones" "162874"
assert "cat case9.assembleContigs.jsonl | head -n 1 | jq -r .longestContigLength" "227"
assert "cat case9.assembleContigs.jsonl | head -n 1 | jq -r .clonesWithAmbiguousLetters" "981"
assert "cat case9.assembleContigs.jsonl | head -n 1 | jq -r .assemblePrematureTerminationEvents" "3"
assert "cat case9.assembleContigs.jsonl | head -n 1 | jq -r .finalCloneCount" "22559"
