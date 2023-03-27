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

  mixcr align -p mikelov-et-al-2021 -b library_for_alleles_test.json trees_samples/$R1 trees_samples/$R2 align/$id.vdjca

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

assert "mixcr exportReportsTable --no-header -foundAllelesCount base_build_trees.shmt" "2"
assert "mixcr exportReportsTable --with-upstreams --no-header -foundAllelesCount base_build_trees.shmt | grep -c '2'" "3"

assert "head -n 1 alleles/report.json | jq -r .statuses.FOUND_KNOWN_VARIANT" "1"
assert "head -n 1 alleles/report.json | jq -r .statuses.DE_NOVO" "1"
assert "head -n 1 alleles/report.json | jq -r '.zygotes.\"2\"'" "1"
assert "head -n 1 alleles/report.json | jq -r '.zygotes.\"1\"'" "10"

# 3 found alleles of IGHV2-70
assert "grep -c 'IGHV2-70' alleles/description.tsv" "3"
# 1 found alleles based on IGHV2-70*01
assert "grep -c 'IGHV2-70\*' alleles/description.tsv" "2"
# 1 found alleles based on IGHV2-70D*01
assert "grep -c 'IGHV2-70D\*04' alleles/description.tsv" "1"
keyOfRelativeMutations=`head -n 1 alleles/description.tsv | sed 's/mutations/#/' | cut -d# -f1 | wc -w  | awk '{ print $1 + 1 }'`
assert "grep 'IGHJ6\*01' alleles/description.tsv | cut -f$keyOfRelativeMutations" "SG17TSG18AST19CSC35A"

assert "grep '\*01' alleles/description.tsv | wc -l" "8"
assert "grep 'IGHV4-55\*00' alleles/description.tsv | wc -l" "1"

keyOfNumberOfCones=`head -n 1 trees/trees.tsv | sed 's/numberOfClonesInTree/#/' | cut -d# -f1 | wc -w  | awk '{ print $1 + 1 }'`
keyOfCDR3=`head -n 1 trees/trees.tsv | sed 's/nSeqCDR3OfMrca/#/' | cut -d# -f1 | wc -w  | awk '{ print $1 + 1 }'`
# biggest tree
# `tail +2` - skip first line with column names
# `sort -n -r -k 2` - reverse numeric sort by second column (uniqClonesCount)
assert "tail +2 trees/trees.tsv | sort -n -r -k $keyOfNumberOfCones | head -n 1 | awk '{print \$$keyOfNumberOfCones}'" "13"
assert "tail +2 trees/trees.tsv | sort -n -r -k $keyOfNumberOfCones | head -n 1 | awk '{print \$$keyOfCDR3}'" "TGTGCCAGAGAAGGATCAGATAGTGCCGGGGGTGCTTTTGATGTCTGG"
