#!/usr/bin/env bash

# The steps `analyze --dry-run` prints for a mitool preset, run one by one in a fresh folder, give the same clones as analyze

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

rm -rf analyze_dry_run_mitool
mkdir -p analyze_dry_run_mitool/analyze analyze_dry_run_mitool/steps

mixcr analyze 10x-sc-xcr-vdj --species hs $R1 $R2 analyze_dry_run_mitool/analyze/sample

mixcr analyze --dry-run 10x-sc-xcr-vdj --species hs $R1 $R2 analyze_dry_run_mitool/steps/sample \
  | grep '^mixcr ' > analyze_dry_run_mitool/steps.txt

assert "head -n 1 analyze_dry_run_mitool/steps.txt | cut -d ' ' -f 2,3" "mitool parse"
assert "grep -c '^mixcr mitool consensus ' analyze_dry_run_mitool/steps.txt" "4"

while IFS= read -r step; do
  # shellcheck disable=SC2086
  $step
done < analyze_dry_run_mitool/steps.txt

for clns in analyze_dry_run_mitool/analyze/*.clns; do
  name=$(basename "$clns")
  expected=$(($(mixcr exportClones --no-header "$clns" | grep -v '^WARNING' | wc -l)))
  [[ $expected -gt 0 ]]
  assert "mixcr exportClones --no-header analyze_dry_run_mitool/steps/$name | grep -v '^WARNING' | wc -l" "$expected"
done

for tsv in analyze_dry_run_mitool/analyze/*.tsv; do
  name=$(basename "$tsv")
  [[ "$name" == *.list.tsv ]] && continue
  cmp "$tsv" "analyze_dry_run_mitool/steps/$name"
done
