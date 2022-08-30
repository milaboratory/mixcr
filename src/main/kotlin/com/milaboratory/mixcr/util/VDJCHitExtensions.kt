/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.util

import com.milaboratory.core.Range
import com.milaboratory.mixcr.basictypes.VDJCHit
import io.repseq.core.GeneFeature

fun VDJCHit.alignmentsCover(geneFeatureToMatch: VJPair<GeneFeature>): Boolean {
    val partitioning = gene.partitioning.getRelativeReferencePoints(alignedFeature)
    val rangesToMatch = partitioning.getRanges(geneFeatureToMatch[geneType])
    return alignmentsCover(rangesToMatch)
}

fun VDJCHit.alignmentsCover(rangesToMatch: Array<Range>): Boolean {
    val alignedRanges = alignments
        .filterNotNull()
        .map { it.sequence1Range }
    return rangesToMatch.all { toMatch ->
        alignedRanges.any { found -> toMatch in found }
    }
}
