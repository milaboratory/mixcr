#!/usr/bin/env bash

#
# Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
#
# Before downloading or accessing the software, please read carefully the
# License Agreement available at:
# https://github.com/milaboratory/mixcr/blob/develop/LICENSE
#
# By downloading or accessing the software, you accept and agree to be bound
# by the terms of the License Agreement. If you do not want to agree to the terms
# of the Licensing Agreement, you must not download or access the software.
#
# Start from BAM integration test

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

mixcr bam2fastq -b unsorted.bam -r1 r1.fq -r2 r2.fq -u unp.fq -f
mixcr align --preset test-generic -s hs unsorted.bam bam.vdjca -f
mixcr align --preset test-generic -s hs r1.fq r2.fq fq.vdjca
mixcr alignmentsDiff bam.vdjca fq.vdjca > diff

assert "cat diff | grep 'Total number of different reads'" "Total number of different reads: 0"
