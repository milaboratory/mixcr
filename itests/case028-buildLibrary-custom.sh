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

set -e

echo ">IGHVXX" > v.fasta
echo "ATCGACTGCACTGACATCGACTAGCTAGCATACA" >> v.fasta

echo ">IGHJXX" > j.fasta
echo "ATCGCTGCACTACGTACTAGCTCA" >> j.fasta

echo ">read1" > case028-seq.fasta
echo "ATCGACTGCCTGACATCGACTAGCTAGCATACATGCGACTGTTCGCTGCACTACGTACAAGCTCA" >> case028-seq.fasta

mixcr buildLibrary --verbose \
  --v-genes-from-fasta v.fasta \
  --v-gene-feature VRegion \
  --j-genes-from-fasta j.fasta \
  --chain IGH \
  --species xxx \
  --do-not-infer-points \
  library_xxx.json.gz

mixcr align -p generic-amplicon --verbose \
  --species xxx \
  --rna \
  --library library_xxx \
  --json-report case028.report.json \
  --floating-left-alignment-boundary \
  --rigid-right-alignment-boundary C \
  case028-seq.fasta case028-xxx.vdjca

assert "cat case028.report.json | head -n 1 | jq -r .totalReadsProcessed" "1"
assert "cat case028.report.json | head -n 1 | jq -r .aligned" "1"
assert "mixcr exportAlignments case028-xxx.vdjca --drop-default-fields --no-header -vHit" "IGHVXX"
assert "mixcr exportAlignments case028-xxx.vdjca --drop-default-fields --no-header -jHit" "IGHJXX"
