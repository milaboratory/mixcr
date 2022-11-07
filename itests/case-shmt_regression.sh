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


FILES=`ls regression/*.shmt`
for filename in $FILES; do
  id=${filename#regression/*}
  id=${id%*.shmt}

  mixcr exportShmTrees "regression/${id}.shmt" "${id}.tsv"

  mixcr exportShmTreesWithNodes "regression/${id}.shmt" "${id}_with_nodes.tsv"

  mixcr exportPlots shmTrees "regression/${id}.shmt" "${id}_plots.pdf"

  mixcr exportShmTreesNewick "regression/${id}.shmt" "${id}_newick"

  mixcr exportReports "regression/${id}.shmt" "${id}_report.txt"
done
