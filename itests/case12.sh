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

mixcr align -f \
    --tag-pattern-name mikelov_et_al_2021 \
    -p kaligner2 -s hs \
    -OvParameters.geneFeatureToAlign=VTranscript \
    -OvParameters.parameters.floatingLeftBound=false \
    -OjParameters.parameters.floatingRightBound=false \
    -OcParameters.parameters.floatingRightBound=false \
    -OallowPartialAlignments=true \
    -OallowNoCDR3PartAlignments=true \
    -OsaveOriginalReads=true \
    --report case12.align.report \
    umi_single_read_R1.fastq \
    umi_single_read_R2.fastq \
    case12.aligned-vdjca

mixcr correctAndSortTags case12.aligned-vdjca case12.corrected-vdjca

mixcr itestAssemblePreClones case12.corrected-vdjca case12.corrected-vdjca.pc

mixcr assemble -f -a case12.corrected-vdjca case12.cdr3-clna
mixcr assembleContigs -f case12.cdr3-clna case12.cdr3-clns

mixcr assemble -f -OassemblingFeatures='VDJRegion' case12.corrected-vdjca case12.vdjregion-clns

mixcr exportClones -nFeature VDJRegion case12.vdjregion-clns case12.vdjregion-clns.txt
mixcr exportClones -nFeature VDJRegion case12.cdr3-clns case12.cdr3-clns.txt

sort case12.vdjregion-clns.txt > case12.vdjregion-clns.txt.s
sort case12.cdr3-clns.txt > case12.cdr3-clns.txt.s

[[ $(cat case12.cdr3-clns.txt.s | wc -l) -eq 2 ]] || exit 1
cmp case12.vdjregion-clns.txt.s case12.cdr3-clns.txt.s
