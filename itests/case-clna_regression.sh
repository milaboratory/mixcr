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


FILES=`ls regression/*.clna`
for filename in $FILES; do
  id=${filename#regression/*}
  id=${id%*.clna}

  mixcr exportPreset --mixcr-file "regression/${id}.clna" "${id}_preset.yaml"

  mixcr exportClonesPretty "regression/${id}.clna" "${id}_export_clones_pretty.txt"

  mixcr exportClones "regression/${id}.clna" "${id}_export_clones.tsv"

  mixcr exportAlignmentsPretty "regression/${id}.clna" "${id}_export_alignments_pretty.txt"

  mixcr exportAlignments "regression/${id}.clna" "${id}_export_alignments.tsv"

  mixcr exportClonesOverlap "regression/${id}.clna" "${id}_overlap.tsv"

  mixcr exportQc align "regression/${id}.clna" "${id}_qc_align.svg"

  mixcr postanalysis individual --default-downsampling none --default-weight-function none "regression/${id}.clna" "${id}_pa.json"

  if grep -q '\- "assembleContigs"' "${id}_preset.yaml"
  then
    mixcr assembleContigs "regression/${id}.clna" "${id}.contigs.clns"
  fi

  mixcr exportReports "regression/${id}.clna" "${id}_report.txt"
done
