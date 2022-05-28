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

set -e

mixcr align -f \
    --tag-pattern '^(CELL:N{16})(UMI:N{10})\^(R2:*)' \
    -p rna-seq -s hs \
    -OvParameters.geneFeatureToAlign=VTranscript \
    -OvParameters.parameters.floatingLeftBound=false \
    -OjParameters.parameters.floatingRightBound=false \
    -OcParameters.parameters.floatingRightBound=false \
    -OallowPartialAlignments=true \
    -OallowNoCDR3PartAlignments=true \
    -OsaveOriginalReads=true \
    --report case10.align.report \
    single_cell_vdj_t_subset_R1.fastq.gz \
    single_cell_vdj_t_subset_R2.fastq.gz \
    case10.aligned-vdjca

mixcr correctAndSortTags case10.aligned-vdjca case10.corrected-vdjca

mixcr assemblePartial case10.corrected-vdjca case10.part-assembled-vdjca

