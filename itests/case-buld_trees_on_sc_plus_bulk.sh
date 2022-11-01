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

mkdir -p "alleles"
mkdir -p "trees"

FILES=`ls trees_samples/sc_plus_bulk/sc/*_R1.fastq.gz`
for filename in $FILES; do
  id=${filename#trees_samples/sc_plus_bulk/sc/*}
  id=${id%*_R1.fastq.gz}
  R1=${id}_R1.fastq.gz
  R2=${id}_R2.fastq.gz

  mixcr analyze 10x-vdj-bcr-full-length -s hsa \
    trees_samples/sc_plus_bulk/sc/$R1 trees_samples/sc_plus_bulk/sc/$R2 $id
done

FILES=`ls trees_samples/sc_plus_bulk/bulk/*_R1.fastq.gz`
for filename in $FILES; do
  id=${filename#trees_samples/sc_plus_bulk/bulk/*}
  id=${id%*_R1.fastq.gz}
  R1=${id}_R1.fastq.gz
  R2=${id}_R2.fastq.gz

  mixcr analyze generic-bcr-amplicon-umi \
    -f --species hsa --rna \
    --tag-pattern "^(R1F:N{0:2}(C:gggggaaaagggttg)(R1:*)) | ^(R1F:N{0:2}(C:rggggaagacsgatg)(R1:*)) | ^(R1F:N{0:2}(C:agcgggaagaccttg)(R1:*)) | ^(R1F:N{0:2}(C:tatgatggggaacac)(R1:*)) \ ^(UMI:NNNNNNNNNNNN)tcttggg(R2:*)" \
    -M align.tagUnstranded=true \
    --floating-left-alignment-boundary --floating-right-alignment-boundary C \
    --remove-step exportClones --assemble-clonotypes-by VDJRegion \
    --split-clones-by C \
    trees_samples/sc_plus_bulk/bulk/$R1 trees_samples/sc_plus_bulk/bulk/$R2 $id
done

mixcr findAlleles \
  -j alleles/report.json \
  -r alleles/report.txt \
  --debugDir alleles/debug \
  --output-template 'alleles/{file_name}.with_alleles.clns' \
  --export-library alleles_library.json \
  --export-library alleles_library.fasta \
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

#assert "cat alleles/report.json | head -n 1 | jq -r .foundAlleles" "3"
#assert "cat alleles/report.json | head -n 1 | jq -r '.zygotes.\"2\"'" "1"
#
#assert "grep 'IGHV2-70' alleles/description.tsv | awk '{print \$6}'" "ST311G\nSG170AST259CST311GSA335T"
#assert "grep 'IGHJ6' alleles/description.tsv | awk '{print \$6}'" "SG37TSG38AST39CSC55A"
#
## biggest tree
## `tail +2` - skip first line with column names
## `sort -n -r -k 2` - reverse numeric sort by second column (uniqClonesCount)
#assert "cat trees/trees.tsv | tail +2 | sort -n -r -k 2 | head -n 1 | awk '{print \$2}'" "13"
#assert "cat trees/trees.tsv | tail +2 | sort -n -r -k 2 | head -n 1 | awk '{print \$6}'" "TGTGCCAGAGAAGGATCAGATAGTGCCGGGGGTGCTTTTGATGTCTGG"

