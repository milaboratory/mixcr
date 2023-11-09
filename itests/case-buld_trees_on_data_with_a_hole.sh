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

  # skip FR1 abd CDR2
  mixcr analyze --verbose --assemble-clonotypes-by '[{CDR1Begin:CDR2Begin},{FR3Begin:FR4End}]' mikelov-et-al-2021 trees_samples/$R1 trees_samples/$R2 $id
done

mixcr findAlleles \
  -j alleles/report.json \
  -r alleles/report.txt \
  --debug-dir alleles/debug \
  --output-template 'alleles/{file_name}.with_alleles.clns' \
  --export-library alleles_library.json \
  --export-library alleles_library.fasta \
  --export-alleles-mutations alleles/description.tsv \
  $(ls *.clns)

mixcr findShmTrees \
  -j trees/report.json \
  -r trees/report.txt \
  --debug-dir trees/debug \
  $(ls alleles/*.clns) trees/result.shmt

mixcr exportShmTrees trees/result.shmt trees/trees.tsv

mixcr exportShmTreesWithNodes trees/result.shmt trees/trees_with_nodes.tsv

mixcr exportPlots shmTrees trees/result.shmt trees/plots.pdf

[[ -f trees/plots.pdf ]] || exit 1

assert "head -n 1 trees/trees_with_nodes.tsv | grep -c nSeqCDR1" "1"
assert "head -n 1 trees/trees_with_nodes.tsv | grep -c nSeqCDR2" "0"

assert "head -n 1 alleles/report.json | jq -r .statuses.FOUND_KNOWN_VARIANT" "2"
assert "cat alleles/report.json | head -n 1 | jq -r '.zygotes.\"2\"'" "1"

keyOfRelativeMutations=`head -n 1 alleles/description.tsv | sed 's/mutations/#/' | cut -d# -f1 | wc -w  | awk '{ print $1 + 1 }'`
assert "grep 'IGHJ6\*05x' alleles/description.tsv | wc -l" "1"

keyOfNumberOfCones=`head -n 1 trees/trees.tsv | sed 's/numberOfClonesInTree/#/' | cut -d# -f1 | wc -w  | awk '{ print $1 + 1 }'`
keyOfCDR3=`head -n 1 trees/trees.tsv | sed 's/nSeqCDR3OfMrca/#/' | cut -d# -f1 | wc -w  | awk '{ print $1 + 1 }'`

expectedBiggestTreeSize=7
expectedBiggestTreeCDR3="TGTGCTGGAGGGCCTAGTATTGGGAGATACGACTACTGG"
# biggest tree
# `tail +2` - skip first line with column names
# `sort -n -r -k $i` - reverse numeric sort by i column (numberOfClonesInTree in this case)
assert "tail +2 trees/trees.tsv | sort -n -r -k $keyOfNumberOfCones | head -n 1 | awk '{print \$$keyOfNumberOfCones}'" "$expectedBiggestTreeSize"

assert "tail +2 trees/trees.tsv | cut -f $keyOfNumberOfCones,$keyOfCDR3 | grep $expectedBiggestTreeSize | cut -f 2 | grep -c $expectedBiggestTreeCDR3" "1"
