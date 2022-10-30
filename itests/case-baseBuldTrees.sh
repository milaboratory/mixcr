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

mkdir -p "align"
mkdir -p "align_by_alleles"
mkdir -p "align_corrected"
mkdir -p "assemble"
mkdir -p "alleles"
mkdir -p "trees"

FILES=`ls trees_samples/*_R1.fastq.gz`
for filename in $FILES; do
  id=${filename#trees_samples/*}
  id=${id%*_R1.fastq.gz}
  R1=${id}_R1.fastq.gz
  R2=${id}_R2.fastq.gz

  mixcr align -p mikelov-et-al-2021 trees_samples/$R1 trees_samples/$R2 align/$id.vdjca

  mixcr refineTagsAndSort align/$id.vdjca align_corrected/$id.vdjca

  mixcr assemble align_corrected/$id.vdjca assemble/$id.clns
done

mixcr findAlleles \
  -j alleles/report.json \
  -r alleles/report.txt \
  --debugDir alleles/debug \
  --output-template 'alleles/{file_name}.with_alleles.clns' \
  --export-library alleles_library.json \
  --export-alleles-mutations alleles/description.tsv \
  $(ls assemble/*.clns)

mixcr findShmTrees \
  -j trees/report.json \
  -r trees/report.txt \
  --debugDir trees/debug \
  $(ls alleles/*.clns) trees/result.shmt

mixcr exportShmTrees trees/result.shmt trees/trees.tsv

mixcr exportShmTreesWithNodes trees/result.shmt trees/trees_with_nodes.tsv

assert "mixcr exportShmTreesWithNodes -readFraction trees/result.shmt | grep -c 'NaN'" "0"

mixcr exportPlots shmTrees trees/result.shmt trees/plots.pdf

[[ -f trees/plots.pdf ]] || exit 1

FILES=`ls trees_samples/*_R1.fastq.gz`
for filename in $FILES; do
  id=${filename#trees_samples/*}
  id=${id%*_R1.fastq.gz}
  R1=${id}_R1.fastq.gz
  R2=${id}_R2.fastq.gz

  mixcr align -p mikelov-et-al-2021 -b alleles_library.json trees_samples/$R1 trees_samples/$R2 align_by_alleles/$id.vdjca
done

assert "cat alleles/report.json | head -n 1 | jq -r .foundAlleles" "3"
assert "cat alleles/report.json | head -n 1 | jq -r '.zygotes.\"2\"'" "1"
assert "cat alleles/report.json | head -n 1 | jq -r .clonesCountWithNegativeScoreChange" "48"

assert "grep 'IGHV2-70' alleles/description.tsv | awk '{print \$6}'" "ST311G\nSG170AST259CST311GSA335T"
assert "grep 'IGHJ6' alleles/description.tsv | awk '{print \$6}'" "SG37TSG38AST39CSC55A"

assert "grep 'Total trees count:' trees/report.txt | head -n 1" "Total trees count: 115"
assert "grep 'Total clones count in trees:' trees/report.txt | head -n 1" "Total clones count in trees: 312"

# biggest tree
# `tail +2` - skip first line with column names
# `sort -n -r -k 2` - reverse numeric sort by second column (uniqClonesCount)
assert "cat trees/trees.tsv | tail +2 | sort -n -r -k 2 | head -n 1 | awk '{print \$2}'" "13"
assert "cat trees/trees.tsv | tail +2 | sort -n -r -k 2 | head -n 1 | awk '{print \$6}'" "TGTGCCAGAGAAGGATCAGATAGTGCCGGGGGTGCTTTTGATGTCTGG"

