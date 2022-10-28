#!/usr/bin/env bash

# Single-cell integration test

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

mixcr analyze -f 10x-vdj-bcr \
  --species hs \
  single_cell_vdj_t_subset_R1.fastq.gz \
  single_cell_vdj_t_subset_R2.fastq.gz \
  case10.raw

mixcr analyze -f 10x-vdj-bcr \
  --species hs \
  --assemble-contigs-by VDJRegion \
  single_cell_vdj_t_subset_R1.fastq.gz \
  single_cell_vdj_t_subset_R2.fastq.gz \
  case10.vdjcontigs

assert "cat case10.vdjcontigs.assembleContigs.report.json | head -n 1 | jq -r .finalCloneCount" "7"

assert "mixcr exportClones --no-header case10.vdjcontigs.contigs.clns | wc -l" "7"
assert "mixcr exportClones --no-header --split-by-tag CELL case10.vdjcontigs.contigs.clns | wc -l" "7"
assert "mixcr exportClones --no-header --split-by-tag UMI case10.vdjcontigs.contigs.clns | wc -l" "60"
assert "mixcr exportClones --no-header -tag UMI case10.vdjcontigs.contigs.clns | wc -l" "60"
assert "mixcr exportClones --no-header -allTags Molecule case10.vdjcontigs.contigs.clns | wc -l" "60"

#mixcr refineTagsAndSort case10.aligned-vdjca case10.corrected-vdjca
#
#mixcr align -f \
#  --preset 10x_vdj_bcr \
#  --species hs \
#  --report case10.align.report \
#  single_cell_vdj_t_subset_R1.fastq.gz \
#  single_cell_vdj_t_subset_R2.fastq.gz \
#  case10.aligned-vdjca
#
#mixcr refineTagsAndSort case10.aligned-vdjca case10.corrected-vdjca

#mixcr align -f \
#  --tag-pattern '^(CELL:N{16})(UMI:N{10})\^(R2:*)' \
#  -p rna-seq -s hs \
#  -OvParameters.geneFeatureToAlign=VTranscript \
#  -OvParameters.parameters.floatingLeftBound=false \
#  -OjParameters.parameters.floatingRightBound=false \
#  -OcParameters.parameters.floatingRightBound=false \
#  -OallowPartialAlignments=true \
#  -OallowNoCDR3PartAlignments=true \
#  -OsaveOriginalReads=true \
#  --report case10.align.report \
#  single_cell_vdj_t_subset_R1.fastq.gz \
#  single_cell_vdj_t_subset_R2.fastq.gz \
#  case10.aligned-vdjca
#
#mixcr correctAndSortTags case10.aligned-vdjca case10.corrected-vdjca
#mixcr correctAndSortTags --dont-correct case10.aligned-vdjca case10.sorted-vdjca
#
#mixcr assemblePartial case10.corrected-vdjca case10.part-assembled-molecule-vdjca
#mixcr assemblePartial --cell-level case10.corrected-vdjca case10.part-assembled-cell-vdjca
#
#mixcr itestAssemblePreClones case10.part-assembled-molecule-vdjca case10.part-assembled-molecule-vdjca.pc case10.part-assembled-molecule-vdjca.pc.als case10.part-assembled-molecule-vdjca.pc.cls
#mixcr itestAssemblePreClones --cell-level case10.part-assembled-cell-vdjca case10.part-assembled-cell-vdjca.pc case10.part-assembled-cell-vdjca.pc.als case10.part-assembled-cell-vdjca.pc.cls
#
#mixcr assemble -f -a case10.part-assembled-molecule-vdjca case10.cdr3-molecule-clna
#mixcr assemble -f -a --cell-level case10.part-assembled-cell-vdjca case10.cdr3-cell-clna
#
#mixcr assembleContigs -f case10.cdr3-molecule-clna case10.cdr3-molecule-clns
#mixcr assembleContigs -f case10.cdr3-cell-clna case10.cdr3-cell-clns
#
##mixcr assemble -f -OassemblingFeatures='VDJRegion' case10.part-assembled-molecule-vdjca case10.vdjregion-molecule-clns
##mixcr assemble -f -OassemblingFeatures='VDJRegion' --cell-level case10.part-assembled-cell-vdjca case10.vdjregion-cell-clns
#
## Testing that clones for which mixcr was able to reconstruct the whole sequence are the same both in molecular and cell barcode assembly modes
#
#for f in case10*-clns; do
#  # -count -uniqueTagCount UMI
#  mixcr exportClones -f --split-by-tag CELL -tag CELL -nFeature CDR3 -nFeature VDJRegion ${f} ${f}.txt
#  grep -v -e $'\t''$' ${f}.txt >${f}.txt.g
#  sort ${f}.txt.g >${f}.txt.g.s
#done
#
#cmp case10.cdr3-cell-clns.txt.g.s case10.cdr3-molecule-clns.txt.g.s
