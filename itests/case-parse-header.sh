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

mixcr analyze -f test-tag-from-header \
  sample_IGH_{{R}}.fastq \
  case_header_parse

assert "cat case_header_parse.TAGCTT.assemble.report.json | head -n 1 | jq .readsInClones" "64"
assert "cat case_header_parse.GAGCTT.assemble.report.json | head -n 1 | jq .readsInClones" "68"
