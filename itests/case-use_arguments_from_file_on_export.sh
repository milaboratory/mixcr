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

mixcr analyze --verbose generic-tcr-amplicon \
  --species hs \
  --rna \
  --floating-left-alignment-boundary \
  --floating-right-alignment-boundary J \
  test_R1.fastq test_R2.fastq result

assert "mixcr exportClones --drop-default-fields -nFeature CDR2 -nMutations CDR2 -aaMutations CDR2 result.clns | head -n 1" "nSeqCDR2\tnMutationsCDR2\taaMutationsCDR2"

touch args.txt
echo '-nFeature CDR2' >> args.txt
echo '-nMutations CDR2' >> args.txt
echo '-aaMutations CDR2' >> args.txt

assert "mixcr exportClones --drop-default-fields @args.txt result.clns | head -n 1" "nSeqCDR2\tnMutationsCDR2\taaMutationsCDR2"
