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

set -e

mixcr align --preset test-generic \
      -s hs \
      -OvParameters.geneFeatureToAlign=VGeneWithP -OsaveOriginalReads=true \
      test_R1.fastq test_R2.fastq case15.vdjca
mixcr assemble case15.vdjca case15.clns
mixcr assemble -a case15.vdjca case15.clna

mixcr slice --ids-file <(mixcr exportAlignments case15.vdjca --no-header --drop-default-fields -readIds -vHit | grep 'TRBV24-1' | awk '{print $1}') case15.vdjca case15.filtered.vdjca
mixcr slice --ids-file <(mixcr exportClones case15.clns --no-header --drop-default-fields -cloneId -vHit | grep 'TRBV24-1' | awk '{print $1}') case15.clns case15.filtered.clns
mixcr slice --ids-file <(mixcr exportClones case15.clna --no-header --drop-default-fields -cloneId -vHit | grep 'TRBV24-1' | awk '{print $1}') case15.clna case15.filtered.clna

# | xargs - used as trim() (see https://stackoverflow.com/questions/369758/how-to-trim-whitespace-from-a-bash-variable)

assert "mixcr exportAlignments case15.filtered.vdjca --drop-default-fields --no-header -vHit | sort | uniq | wc -l | xargs" "1"

assert "mixcr exportClones --dont-split-files case15.filtered.clns --drop-default-fields --no-header -vHit | sort | uniq | wc -l | xargs" "1"

assert "mixcr exportClones --dont-split-files case15.filtered.clna --drop-default-fields --no-header -vHit | sort | uniq | wc -l | xargs" "1"
