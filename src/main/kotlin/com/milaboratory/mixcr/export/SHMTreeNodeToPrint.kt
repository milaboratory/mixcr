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
package com.milaboratory.mixcr.export

import com.milaboratory.core.Range
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters
import com.milaboratory.mixcr.trees.CloneOrFoundAncestor
import com.milaboratory.mixcr.trees.MutationsUtils
import com.milaboratory.mixcr.trees.RootInfo
import com.milaboratory.mixcr.util.extractAbsoluteMutations
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.ReferencePoints
import io.repseq.core.VDJCGene
import io.repseq.core.VDJCGeneId

class SHMTreeNodeToPrint(
    val cloneOrFoundAncestor: CloneOrFoundAncestor,
    val rootInfo: RootInfo,
    private val assemblerParameters: CloneAssemblerParameters,
    private val alignerParameters: VDJCAlignerParameters,
    private val geneSupplier: (VDJCGeneId) -> VDJCGene
) {
    val assemblingFeatures: Array<GeneFeature> get() = assemblerParameters.assemblingFeatures

    fun targetNSequence(geneFeature: GeneFeature): NucleotideSequence? =
        when (geneFeature) {
            GeneFeature.CDR3 -> {
                val mutationsSet = cloneOrFoundAncestor.mutationsSet
                mutationsSet.VMutations.buildPartInCDR3(rootInfo)
                    .concatenate(mutationsSet.NDNMutations.buildSequence(rootInfo))
                    .concatenate(mutationsSet.JMutations.buildPartInCDR3(rootInfo))
            }
            else -> when {
                assemblingFeatures.none { geneFeature in it } -> null
                else -> when (val geneType = geneFeature.geneType) {
                    null -> null
                    else -> {
                        val (relativeRangeOfFeature, mutations) = mutationForGeneFeature(geneFeature)
                        MutationsUtils.buildSequence(
                            rootInfo.getSequence1(geneType),
                            mutations,
                            relativeRangeOfFeature
                        )
                    }
                }
            }
        }

    fun mutationForGeneFeature(geneFeature: GeneFeature): Pair<Range, Mutations<NucleotideSequence>> {
        val geneType = geneFeature.geneType
        val geneFeatureToAlign = geneFeatureToAlign(geneType)
        val relativeRangeOfFeature = relativeRangeOfFeature(partitioning(geneType), geneFeatureToAlign, geneFeature)
        val mutations = cloneOrFoundAncestor.mutationsSet.getGeneMutations(geneType)
            .combinedMutations()
            .extractAbsoluteMutations(
                relativeRangeOfFeature,
                isIncludeFirstInserts = geneFeatureToAlign.firstPoint == geneFeature.firstPoint
            )
        return relativeRangeOfFeature to mutations
    }

    private fun geneFeatureToAlign(geneType: GeneType): GeneFeature =
        alignerParameters.getGeneAlignerParameters(geneType).geneFeatureToAlign

    fun partitioning(geneType: GeneType): ReferencePoints =
        geneSupplier(rootInfo.VJBase.getGeneId(geneType)).partitioning

}

private fun relativeRangeOfFeature(
    partitioning: ReferencePoints,
    geneFeatureToAlign: GeneFeature,
    geneFeature: GeneFeature
): Range = Range(
    partitioning.getRelativePosition(geneFeatureToAlign, geneFeature.firstPoint),
    partitioning.getRelativePosition(geneFeatureToAlign, geneFeature.lastPoint)
)
