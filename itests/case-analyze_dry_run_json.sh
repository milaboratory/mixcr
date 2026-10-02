#!/usr/bin/env bash

# Runs the steps that `analyze --dry-run-json` writes for a mitool preset, one by one, in a fresh folder.
# Checks that they give the same clones as analyze.

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

R1=single_cell_vdj_t_subset_R1.fastq.gz
R2=single_cell_vdj_t_subset_R2.fastq.gz

rm -rf analyze_dry_run_json
mkdir -p analyze_dry_run_json/analyze analyze_dry_run_json/steps

mixcr analyze 10x-sc-xcr-vdj --species hs $R1 $R2 analyze_dry_run_json/analyze/sample

mixcr analyze --dry-run-json analyze_dry_run_json/steps.json 10x-sc-xcr-vdj --species hs $R1 $R2 analyze_dry_run_json/steps/sample

assert "jq -r '.steps[0][0:2] | join(\" \")' analyze_dry_run_json/steps.json" "mitool parse"
assert "jq '[.steps[] | select(.[0:2] == [\"mitool\", \"consensus\"])] | length' analyze_dry_run_json/steps.json" "4"

steps=$(jq '.steps | length' analyze_dry_run_json/steps.json)
for ((i = 0; i < steps; i++)); do
  argv=()
  while IFS= read -r arg; do argv+=("$arg"); done < <(jq -r ".steps[$i][]" analyze_dry_run_json/steps.json)
  mixcr "${argv[@]}"
done

for clns in analyze_dry_run_json/analyze/*.clns; do
  name=$(basename "$clns")
  expected=$(($(mixcr exportClones --no-header "$clns" | grep -v '^WARNING' | wc -l)))
  [[ $expected -gt 0 ]]
  assert "mixcr exportClones --no-header analyze_dry_run_json/steps/$name | grep -v '^WARNING' | wc -l" "$expected"
done

for tsv in analyze_dry_run_json/analyze/*.tsv; do
  name=$(basename "$tsv")
  [[ "$name" == *.list.tsv ]] && continue
  cmp "$tsv" "analyze_dry_run_json/steps/$name"
done
