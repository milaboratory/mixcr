#!/usr/bin/env bash

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
  --debug-dir alleles/debug \
  --output-template 'alleles/{file_name}.with_alleles.clns' \
  --export-library alleles_library.json \
  --export-library alleles_library.fasta \
  --export-alleles-mutations alleles/description.tsv \
  $(ls assemble/*.clns)

mixcr findShmTrees \
  -j trees/report.json \
  -r trees/report.txt \
  --debug-dir trees/debug \
  $(ls alleles/*.clns) base_build_trees.shmt

mixcr exportShmTrees base_build_trees.shmt trees/trees.tsv

mixcr exportShmTreesWithNodes base_build_trees.shmt trees/trees_with_nodes.tsv

assert "mixcr exportShmTreesWithNodes -readFraction base_build_trees.shmt | grep -c 'NaN'" "0"

# no other columns if something specified
assert "mixcr exportShmTreesWithNodes -cloneId base_build_trees.shmt | head -n 1 | wc -w" "1"

mixcr exportPlots shmTrees base_build_trees.shmt trees/plots.pdf

[[ -f trees/plots.pdf ]] || exit 1

mixcr exportReportsTable base_build_trees.shmt trees/total_report.tsv
mixcr exportReportsTable --with-upstreams -p full base_build_trees.shmt trees/total_report_with_upstream.tsv

FILES=`ls trees_samples/*_R1.fastq.gz`
for filename in $FILES; do
  id=${filename#trees_samples/*}
  id=${id%*_R1.fastq.gz}
  R1=${id}_R1.fastq.gz
  R2=${id}_R2.fastq.gz

  mixcr align -p mikelov-et-al-2021 -b alleles_library.json trees_samples/$R1 trees_samples/$R2 align_by_alleles/$id.vdjca
done

assert "mixcr exportReportsTable --no-header base_build_trees.shmt | wc -l" "1"
assert "mixcr exportReportsTable --with-upstreams --no-header base_build_trees.shmt | wc -l" "3"

assert "mixcr exportReportsTable --no-header -foundAllelesCount base_build_trees.shmt | grep -c '3'" "1"
assert "mixcr exportReportsTable --with-upstreams --no-header -foundAllelesCount base_build_trees.shmt | grep -c '3'" "3"

assert "head -n 1 alleles/report.json | jq -r .foundAlleles" "3"
assert "head -n 1 alleles/report.json | jq -r '.zygotes.\"2\"'" "1"

assert "grep 'IGHV2-70' alleles/description.tsv | cut -f7" "ST311G\nSG170AST259CST311GSA335T"
assert "grep 'IGHJ6' alleles/description.tsv | cut -f7" "SG37TSG38AST39CSC55A"

# biggest tree
# `tail +2` - skip first line with column names
# `sort -n -r -k 2` - reverse numeric sort by second column (uniqClonesCount)
assert "tail +2 trees/trees.tsv | sort -n -r -k 2 | head -n 1 | awk '{print \$2}'" "13"
assert "tail +2 trees/trees.tsv | sort -n -r -k 2 | head -n 1 | awk '{print \$6}'" "TGTGCCAGAGAAGGATCAGATAGTGCCGGGGGTGCTTTTGATGTCTGG"
