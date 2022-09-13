#!/usr/bin/env bash

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

mixcr align -s hs -OvParameters.geneFeatureToAlign=VGeneWithP -OsaveOriginalReads=true test_R1.fastq test_R2.fastq case15.vdjca
mixcr filterAlignments --read-ids-file <(mixcr exportAlignments case15.vdjca --no-headers -readId -vHit | grep 'TRBV24-1' | awk '{print $1}') case15.vdjca case15.filtered.vdjca

assert "mixcr exportAlignments case15.vdjca --no-headers -vHit | sort | uniq | wc -l" "28"
assert "mixcr exportAlignments case15.filtered.vdjca --no-headers -vHit | sort | uniq | wc -l" "1"
