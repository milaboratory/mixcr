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

mixcr buildLibrary --verbose \
  --v-genes-from-species human \
  --d-genes-from-species mouse \
  --j-genes-from-species spalax \
  --c-genes-from-species alpaca \
  --chain IGH \
  --species kozel \
  library_kozel.IGH.json

mixcr buildLibrary --verbose \
  --v-genes-from-species human \
  --j-genes-from-species rat \
  --c-genes-from-species rhesus \
  --chain TRA \
  --species kozel \
  library_kozel.TRA.json

mixcr mergeLibrary \
  library_kozel.IGH.json \
  library_kozel.TRA.json \
  library_kozel.json

echo ">IGH" > case027-seq.fasta
echo "CAGGTCACCTTGAGGGAGTCTGGTCCTGCGCTGGTGAAACCCACACAGACCCTCACACTGACCTGCACCTTCTCTGGGTTCTCACTCAGCACTAGTGGAATGTGTGTGAGCTGGATCCGTCAGCCCCCAGGGAAGGCCCTGGAGTGGCTTGCACTCATTGATTGGGATGATGATAAATACTACAGCACATCTCTGAAGACCAGGCTCACCATCTCCAAGGACACCTCCAAAAACCAGGTGGTCCTTACAATGACCAACATGGACCCTGTGGACACAGCCACGTATTACTGTGCACGGATACTCTCCTACTATAGTAACTACCTACTACGATTTCTGGGGCCAGGGGACCCTGGTCACCGTCTCCTCCGGAGAGCTCGTCTGCCCCGACACTCTTCCCCCTCGCCTCCTGTGAGAGCCCCGTGTC" >> case027-seq.fasta
echo ">TRA" >> case027-seq.fasta
echo "GCCCAGTCGGTGACCCAGCTTGGCAGCCACGTCTCTGTCTCTGAGGGAGCCCTGGTTCTGCTGAGGTGCAACTACTCATCGTCTGTTCCACCATATCTCTTCTGGTATGTGCAATACCCCAACCAAGGACTCCAGCTTCTCCTGAAGTACACAACAGGGGCCACCCTGGTTAAAGGCATCAACGGTTTTGAGGCTGAATTTAAGAAGAGTGAAACCTCCTTCCACCTGACGAAACCCTCAGCCCATATGAGCGACGCGGCTGAGTACTTCTGTGCTGTGAGTGATGCTATGGATTGCAACTATCAGTTGATCTGGGGCTCTGGGACCAAGCTAATTATAAAGCCAGATATCCAGAACCCTGACCCTGCCGTGTACCAGCTGAGAGGCTCTAAATCCAATGACACC" >> case027-seq.fasta

mixcr align -p generic-amplicon --verbose \
            --species kozel \
            --rna \
            --library library_kozel \
            --json-report case027.report.json \
            --floating-left-alignment-boundary \
            --rigid-right-alignment-boundary C \
            case027-seq.fasta case027-kozel.vdjca

assert "cat case027.report.json | head -n 1 | jq -r .totalReadsProcessed" "2"
assert "cat case027.report.json | head -n 1 | jq -r .aligned" "2"
assert "cat case027.report.json | head -n 1 | jq -r .chainUsage.chains.TRA.total" "1"
assert "cat case027.report.json | head -n 1 | jq -r .chainUsage.chains.IGH.total" "1"
