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

  mixcr align -p mikelov-et-al-2021 -b sligtly_broken_library_for_alleles_test.json trees_samples/$R1 trees_samples/$R2 align/$id.vdjca

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

assert "head -n 1 alleles/report.json | jq -r .statuses.FOUND_KNOWN_VARIANT" "5"
assert "head -n 1 alleles/report.json | jq -r .statuses.DE_NOVA" "3"
assert "head -n 1 alleles/report.json | jq -r '.zygotes.\"2\"'" "1"
