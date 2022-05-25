package com.milaboratory.mixcr.alleles

import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.sequence.NucleotideSequence

internal class CombinedAllelesSearcher(
    private val parameters: FindAllelesParameters,
    private val scoring: AlignmentScoring<NucleotideSequence>,
    private val sequence1: NucleotideSequence
) : AllelesSearcher {
    override fun search(clones: List<CloneDescription>): List<AllelesSearcher.Result> {
        return ByNativeCellsSearcher(parameters.minDiversityForNativeAlleles).search(clones)
    }

}
