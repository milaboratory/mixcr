#!/usr/bin/env bash

assert() {
  expected=$(echo -ne "${2:-}")
  result="$(eval 2>/dev/null $1)" || true
  result="$(sed -e :a -e '$!N;s/\n/\\n/;ta' -e 's/ *$//' -e 's/^ *//' <<<"$result")"
  if [[ "$result" == "$expected" ]]; then
    return
  fi
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

mkdir -p "alleles_1"
cd alleles_1
mixcr findAlleles \
  -j report.json \
  -r report.txt \
  --debug-dir debug \
  --output-template '{file_name}.with_alleles.clns' \
  --export-library alleles_library.json \
  --export-library alleles_library.fasta \
  --export-alleles-mutations description.tsv \
  $(ls ../assemble/*.clns)
file=`ls *.with_alleles.clns | head -n 1`
mixcr exportReports --yaml "$file" alleles.report.yaml
cd ../

mkdir -p "alleles_2"
cd alleles_2
mixcr findAlleles \
  -j report.json \
  -r report.txt \
  --debug-dir debug \
  --output-template '{file_name}.with_alleles.clns' \
  --export-library alleles_library.json \
  --export-library alleles_library.fasta \
  --export-alleles-mutations description.tsv \
  $(ls ../assemble/*.clns)
file=`ls *.with_alleles.clns | head -n 1`
mixcr exportReports --yaml "$file" alleles.report.yaml
cd ../

if ! cmp alleles_1/alleles.report.yaml alleles_2/alleles.report.yaml; then
  diff alleles_1/alleles.report.yaml alleles_2/alleles.report.yaml
fi

first_sha=$(shasum alleles_1/alleles_library.json | awk '{print $1}')
assert "shasum alleles_2/alleles_library.json | awk '{print \$1}'" "$first_sha"

if ! cmp alleles_1/alleles_library.fasta alleles_2/alleles_library.fasta; then
  diff alleles_1/alleles_library.fasta alleles_2/alleles_library.fasta
fi

if ! cmp alleles_1/description.tsv alleles_2/description.tsv; then
  diff alleles_1/description.tsv alleles_2/description.tsv
fi

FILES=`ls trees_samples/*_R1.fastq.gz`
for filename in $FILES; do
  id=${filename#trees_samples/*}
  id=${id%*_R1.fastq.gz}

  first_sha=$(shasum "alleles_1/${id}.with_alleles.clns" | awk '{print $1}')
  assert "shasum alleles_2/${id}.with_alleles.clns | awk '{print \$1}'" "$first_sha"
done

mkdir -p "trees_1"
cd trees_1
mixcr findShmTrees \
  -j report.json \
  -r report.txt \
  --debug-dir debug \
  $(ls ../alleles_1/*.clns) result.shmt
mixcr exportReports --yaml result.shmt result.report.yaml

mkdir -p debug_sorted
FILES=`ls debug/*`
for filename in $FILES; do
  filename=${filename#debug/*}
  head -n 1 "debug/$filename" > "debug_sorted/$filename"
  tail +2 "debug/$filename" | sort >> "debug_sorted/$filename"
done

cd ../

mkdir -p "trees_2"
cd trees_2
mixcr findShmTrees \
  -j report.json \
  -r report.txt \
  --debug-dir debug \
  $(ls ../alleles_1/*.clns) result.shmt
mixcr exportReports --yaml result.shmt result.report.yaml

mkdir -p debug_sorted
FILES=`ls debug/*`
for filename in $FILES; do
  filename=${filename#debug/*}
  head -n 1 "debug/$filename" > "debug_sorted/$filename"
  tail +2 "debug/$filename" | sort >> "debug_sorted/$filename"
done
cd ../

FILES=`ls trees_1/debug/step_*`
for filename in $FILES; do
  filename=${filename#trees_1/debug/*}
  if ! cmp "trees_1/debug_sorted/$filename" "trees_2/debug_sorted/$filename"; then
    diff "trees_1/debug_sorted/$filename" "trees_2/debug_sorted/$filename"
  fi
done

if ! cmp trees_1/result.report.yaml trees_2/result.report.yaml; then
  diff trees_1/result.report.yaml trees_2/result.report.yaml
fi

first_sha=$(shasum trees_1/result.shmt | awk '{print $1}')
assert "shasum trees_2/result.shmt | awk '{print \$1}'" "$first_sha"
