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

mkdir -p result_1
cd result_1

mixcr analyze 10x-vdj-bcr \
  --species hs \
  --assemble-contigs-by VDJRegion \
  ../single_cell_vdj_t_subset_R1.fastq.gz \
  ../single_cell_vdj_t_subset_R2.fastq.gz \
  result

mixcr exportReports --yaml result.contigs.clns result_report.yaml
cd ../

mkdir -p result_2
cd result_2

mixcr analyze 10x-vdj-bcr \
  --species hs \
  --assemble-contigs-by VDJRegion \
  ../single_cell_vdj_t_subset_R1.fastq.gz \
  ../single_cell_vdj_t_subset_R2.fastq.gz \
  result

mixcr exportReports --yaml result.contigs.clns result_report.yaml
cd ../

if ! cmp result_1/result_report.yaml result_2/result_report.yaml; then
  diff result_1/result_report.yaml result_2/result_report.yaml
fi

first_sha=$(shasum result_1/result.vdjca | awk '{print $1}')
assert "shasum result_2/result.vdjca | awk '{print \$1}'" "$first_sha"

first_sha=$(shasum result_1/result.refined.vdjca | awk '{print $1}')
assert "shasum result_2/result.refined.vdjca | awk '{print \$1}'" "$first_sha"

first_sha=$(shasum result_1/result.clna | awk '{print $1}')
assert "shasum result_2/result.clna | awk '{print \$1}'" "$first_sha"

first_sha=$(shasum result_1/result.contigs.clns | awk '{print $1}')
assert "shasum result_2/result.contigs.clns | awk '{print \$1}'" "$first_sha"

