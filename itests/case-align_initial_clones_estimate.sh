#!/usr/bin/env bash

# The align report's estimatedInitialClones tracks assemble's initialClonesCreated on bulk amplicon data.

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

set -euxo pipefail

mixcr analyze generic-amplicon -f \
  --species hs --rna \
  --floating-left-alignment-boundary --floating-right-alignment-boundary C \
  --assemble-clonotypes-by CDR3 \
  --split-clones-by C \
  CD4M1_test_R1.fastq.gz CD4M1_test_R2.fastq.gz \
  case_estimate

estimate=$(head -n 1 case_estimate.align.report.json | jq -r .estimatedInitialClones)
created=$(head -n 1 case_estimate.assemble.report.json | jq -r .initialClonesCreated)
assert "[[ $created -gt 100 ]] && echo ok" "ok"
# Within 5 %: the sketch's error is 0.81 % and bulk amplicon has no consensus step to diverge on.
assert "[[ $((estimate * 100)) -ge $((created * 95)) && $((estimate * 100)) -le $((created * 105)) ]] && echo ok" "ok"
