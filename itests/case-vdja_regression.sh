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

set -euxo pipefail


FILES=`ls regression/*.vdjca`
for filename in $FILES; do
  id=${filename#regression/*}
  id=${id%*.vdjca}

  mixcr exportPreset --mixcr-file "regression/${id}.vdjca" "${id}_preset.yaml"

  mixcr exportAlignmentsPretty "regression/${id}.vdjca" "${id}_export_pretty.txt"

  mixcr exportAlignments "regression/${id}.vdjca" "${id}_export.tsv"

  mixcr exportQc coverage "regression/${id}.vdjca" "${id}_qc_coverage.svg"

  mixcr exportQc align "regression/${id}.vdjca" "${id}_qc_align.svg"

  input="regression/${id}.vdjca"
  if grep -q '\- "refineTagsAndSort"' "${id}_preset.yaml"
  then
    mixcr refineTagsAndSort "$input" "${id}.refined.vdjca"
    input="${id}.refined.vdjca"
  fi

  if grep -q '\- "assemblePartial"' "${id}_preset.yaml"
  then
    mixcr assemblePartial "$input" "${id}.partial.vdjca"
    input="${id}.partial.vdjca"
  fi

  if grep -q 'clnaOutput: true' "${id}_preset.yaml"
  then
    mixcr assemble "$input" "${id}.clna"
  else
    mixcr assemble "$input" "${id}.clns"
  fi

  mixcr exportReports "regression/${id}.vdjca" "${id}_report.txt"
done
