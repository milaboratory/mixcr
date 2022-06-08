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

set -euxo pipefail

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
    --report case10.align.report \
    umi_ig_data_2_subset_R1.fastq.gz \
    umi_ig_data_2_subset_R2.fastq.gz \
    case11.aligned-vdjca

mixcr correctAndSortTags case11.aligned-vdjca case11.corrected-vdjca

mixcr itestAssemblePreClones case11.corrected-vdjca case11.corrected-vdjca.pc

mixcr assemble -f -a case11.corrected-vdjca case11.clna
