#!/usr/bin/env bash

# Placement of pipeline intermediates

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

# Default placement: every file lands next to the output
mixcr analyze 10x-sc-xcr-vdj-fast --species hs "$R1" "$R2" im.default

assert "ls im.default.parsed.mic im.default.refined.mic im.default.alignments.vdjca | wc -l" "3"
assert "ls im.default.assembledCells.clns im.default.qc.json | wc -l" "2"

# --intermediates-dir moves the files later steps read, and only those
rm -rf scratch
mkdir scratch
mixcr analyze 10x-sc-xcr-vdj-fast --species hs --intermediates-dir ./scratch "$R1" "$R2" im.dir

assert "ls im.dir.*.mic im.dir.*.vdjca im.dir.*.clna | wc -l" "0"
assert "ls scratch/im.dir.parsed.mic scratch/im.dir.refined.mic scratch/im.dir.alignments.vdjca | wc -l" "3"
assert "ls im.dir.assembledCells.clns im.dir.qc.txt im.dir.qc.json im.dir.align.report.txt im.dir.align.report.json | wc -l" "5"
rm -rf scratch

# --intermediates-in-temp leaves only deliverables behind, and owns the folder it used.
# TMPDIR is set so the test asserts against a temp folder it controls.
rm -rf tmpcheck
mkdir tmpcheck
TMPDIR="$(pwd)/tmpcheck" mixcr analyze 10x-sc-xcr-vdj-fast --species hs \
  --intermediates-in-temp --remove-intermediates "$R1" "$R2" im.temp

assert "ls im.temp.*.mic im.temp.*.vdjca im.temp.*.clna | wc -l" "0"
assert "ls im.temp.assembledCells.clns im.temp.qc.json | wc -l" "2"
assert "find tmpcheck -mindepth 1 | wc -l" "0"
rm -rf tmpcheck

# A file given an explicit path is a deliverable and outlives --remove-intermediates
rm -rf tmpcheck
mkdir tmpcheck
TMPDIR="$(pwd)/tmpcheck" mixcr analyze 10x-sc-xcr-vdj-fast --species hs \
  --intermediates-in-temp --remove-intermediates \
  --output-path im.pin.parsed.mic=./im.pin.kept.mic "$R1" "$R2" im.pin

assert "ls im.pin.kept.mic im.pin.assembledCells.clns | wc -l" "2"
assert "ls im.pin.*.vdjca | wc -l" "0"
rm -rf tmpcheck

# --intermediates-dir and --remove-intermediates together: files land in the folder and are
# freed as the run proceeds
rm -rf scratch
mkdir scratch
mixcr analyze 10x-sc-xcr-vdj-fast --species hs \
  --intermediates-dir ./scratch --remove-intermediates "$R1" "$R2" im.both

assert "ls im.both.assembledCells.clns | wc -l" "1"
assert "find scratch -type f | wc -l" "0"
rm -rf scratch

# Sample-split input: the list files each step writes hold bare names, so they have to be
# resolved against the folder that step wrote to
rm -rf scratch
mkdir scratch
mixcr analyze single-cell-as-sample-split --species hs --rna \
  --floating-left-alignment-boundary --floating-right-alignment-boundary C \
  --assemble-clonotypes-by CDR3 --assemble-contigs-by-cells \
  --intermediates-dir ./scratch "$R1" "$R2" im.split

assert "ls im.split.sample0.clns im.split.sample1.clns im.split.sample2.clns | wc -l" "3"
assert "ls im.split.*.vdjca | wc -l" "0"
assert "ls scratch/im.split.sample0.alignments.vdjca scratch/im.split.sample1.alignments.vdjca scratch/im.split.sample2.alignments.vdjca | wc -l" "3"
rm -rf scratch

# A name that matches nothing the run produces is rejected rather than silently ignored. The
# names are checked before any step runs, so this costs nothing.
assert "mixcr analyze 10x-sc-xcr-vdj-fast --species hs --output-path im.typo.parsed.micWRONG=./x.mic $R1 $R2 im.typo 2>&1 | grep -c 'does not match any file this run produces'" "1"
