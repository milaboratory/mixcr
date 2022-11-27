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

mixcr analyze 10x-vdj-bcr \
  --species hs \
  single_cell_vdj_t_subset_R1.fastq.gz \
  single_cell_vdj_t_subset_R2.fastq.gz \
  base_single_cell.raw

mixcr analyze 10x-vdj-bcr \
  --species hs \
  --assemble-contigs-by VDJRegion \
  single_cell_vdj_t_subset_R1.fastq.gz \
  single_cell_vdj_t_subset_R2.fastq.gz \
  base_single_cell.vdjcontigs

assert "cat base_single_cell.vdjcontigs.assembleContigs.report.json | head -n 1 | jq -r .finalCloneCount" "7"

assert "mixcr exportClones --no-header base_single_cell.vdjcontigs.contigs.clns | wc -l" "7"
assert "mixcr exportClones --no-header --split-by-tags Cell base_single_cell.vdjcontigs.contigs.clns | wc -l" "7"
assert "mixcr exportClones --no-header --split-by-tags Molecule base_single_cell.vdjcontigs.contigs.clns | wc -l" "60"
assert "mixcr exportClones --no-header -tags Molecule base_single_cell.vdjcontigs.contigs.clns | wc -l" "60"

#mixcr refineTagsAndSort base_single_cell.aligned-vdjca base_single_cell.corrected-vdjca
#
#mixcr align -f \
#  --preset 10x_vdj_bcr \
#  --species hs \
#  --report base_single_cell.align.report \
#  single_cell_vdj_t_subset_R1.fastq.gz \
#  single_cell_vdj_t_subset_R2.fastq.gz \
#  base_single_cell.aligned-vdjca
#
#mixcr refineTagsAndSort base_single_cell.aligned-vdjca base_single_cell.corrected-vdjca

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
#  --report base_single_cell.align.report \
#  single_cell_vdj_t_subset_R1.fastq.gz \
#  single_cell_vdj_t_subset_R2.fastq.gz \
#  base_single_cell.aligned-vdjca
#
#mixcr correctAndSortTags base_single_cell.aligned-vdjca base_single_cell.corrected-vdjca
#mixcr correctAndSortTags --dont-correct base_single_cell.aligned-vdjca base_single_cell.sorted-vdjca
#
#mixcr assemblePartial base_single_cell.corrected-vdjca base_single_cell.part-assembled-molecule-vdjca
#mixcr assemblePartial --cell-level base_single_cell.corrected-vdjca base_single_cell.part-assembled-cell-vdjca
#
#mixcr itestAssemblePreClones base_single_cell.part-assembled-molecule-vdjca base_single_cell.part-assembled-molecule-vdjca.pc base_single_cell.part-assembled-molecule-vdjca.pc.als base_single_cell.part-assembled-molecule-vdjca.pc.cls
#mixcr itestAssemblePreClones --cell-level base_single_cell.part-assembled-cell-vdjca base_single_cell.part-assembled-cell-vdjca.pc base_single_cell.part-assembled-cell-vdjca.pc.als base_single_cell.part-assembled-cell-vdjca.pc.cls
#
#mixcr assemble -f -a base_single_cell.part-assembled-molecule-vdjca base_single_cell.cdr3-molecule-clna
#mixcr assemble -f -a --cell-level base_single_cell.part-assembled-cell-vdjca base_single_cell.cdr3-cell-clna
#
#mixcr assembleContigs -f base_single_cell.cdr3-molecule-clna base_single_cell.cdr3-molecule-clns
#mixcr assembleContigs -f base_single_cell.cdr3-cell-clna base_single_cell.cdr3-cell-clns
#
##mixcr assemble -f -OassemblingFeatures='VDJRegion' base_single_cell.part-assembled-molecule-vdjca base_single_cell.vdjregion-molecule-clns
##mixcr assemble -f -OassemblingFeatures='VDJRegion' --cell-level base_single_cell.part-assembled-cell-vdjca base_single_cell.vdjregion-cell-clns
#
## Testing that clones for which mixcr was able to reconstruct the whole sequence are the same both in molecular and cell barcode assembly modes
#
#for f in base_single_cell*-clns; do
#  # -count -uniqueTagCount UMI
#  mixcr exportClones -f --split-by-tag CELL -tag CELL -nFeature CDR3 -nFeature VDJRegion ${f} ${f}.txt
#  grep -v -e $'\t''$' ${f}.txt >${f}.txt.g
#  sort ${f}.txt.g >${f}.txt.g.s
#done
#
#cmp base_single_cell.cdr3-cell-clns.txt.g.s base_single_cell.cdr3-molecule-clns.txt.g.s
