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
package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.TranslationParameters
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.ReferencePoints
import io.repseq.core.VDJCGene
import io.repseq.core.VDJCGeneId
import io.repseq.core.VDJCLibraryRegistry
import java.math.BigDecimal

data class SHMTreeForPostanalysis(
    val tree: Tree<Node>,
    val rootInfo: RootInfo,
    val treeId: TreeId
) {
    class Node(
        private val main: CloneOrFoundAncestor,
        private val parent: CloneOrFoundAncestor?,
        private val mostRecentCommonAncestor: CloneOrFoundAncestor,
        val distanceFromParent: BigDecimal?,
        private val rootInfo: RootInfo,
        private val assemblerParameters: CloneAssemblerParameters,
        private val alignerParameters: VDJCAlignerParameters,
        private val geneSupplier: (VDJCGeneId) -> VDJCGene
    ) {
        val clone: Clone? = main.clone

        fun mutationsWithRange(
            geneFeature: GeneFeature,
            relativeTo: GeneFeature? = null,
            baseOn: Base = Base.root
        ): MutationsDescription? {
            if (!canExtractMutations(geneFeature, relativeTo)) return null
            val mainMutations = main.mutationsSet.mutationsWithRange(geneFeature, relativeTo)
            return when (baseOn) {
                Base.root -> mainMutations
                Base.mrca -> mainMutations.differenceWith(
                    mostRecentCommonAncestor.mutationsSet.mutationsWithRange(geneFeature, relativeTo)
                )
                Base.parent -> when (parent) {
                    null -> null
                    else -> mainMutations.differenceWith(
                        parent.mutationsSet.mutationsWithRange(geneFeature, relativeTo)
                    )
                }
            }
        }

        private fun canExtractMutations(geneFeature: GeneFeature, relativeTo: GeneFeature?): Boolean =
            when (geneFeature) {
                GeneFeature.CDR3 -> {
                    require(relativeTo == null)
                    true
                }
                else -> when {
                    assemblerParameters.assemblingFeatures.none { geneFeature in it } -> false
                    else -> geneFeature.geneType != null
                }
            }

        //TODO work with all geneFeatures, for example Exon2 and JRegionTrimmed
        private fun MutationsSet.mutationsWithRange(
            geneFeature: GeneFeature,
            relativeTo: GeneFeature?
        ): MutationsDescription = when (geneFeature) {
            GeneFeature.CDR3 -> {
                require(relativeTo == null)
                val baseCDR3 = rootInfo.baseCDR3()
                MutationsDescription(
                    baseCDR3,
                    mutationsOfCDR3(),
                    Range(0, baseCDR3.size()),
                    TranslationParameters.FromLeftWithoutIncompleteCodon,
                    isIncludeFirstInserts = true
                )
            }
            else -> {
                val geneType = geneFeature.geneType
                val geneFeatureToAlign = alignerParameters.getGeneAlignerParameters(geneType).geneFeatureToAlign
                val partitioning = partitioning(geneType)
                val relativeSeq1Range = Range(
                    partitioning.getRelativePosition(geneFeatureToAlign, geneFeature.firstPoint),
                    partitioning.getRelativePosition(geneFeatureToAlign, geneFeature.lastPoint)
                )
                val mutations = getGeneMutations(geneType).combinedMutations()
                val isIncludeFirstInserts = geneFeatureToAlign.firstPoint == geneFeature.firstPoint
                val translationParameters = partitioning.getTranslationParameters(geneFeature)
                when (relativeTo) {
                    null -> MutationsDescription(
                        rootInfo.getSequence1(geneType),
                        mutations,
                        relativeSeq1Range,
                        translationParameters,
                        isIncludeFirstInserts
                    )
                    else -> {
                        val shift = partitioning.getRelativePosition(geneFeature, relativeTo.firstPoint)
                        val sequence1 = rootInfo.getSequence1(geneType)
                        MutationsDescription(
                            sequence1.getRange(shift, sequence1.size()),
                            mutations.move(-shift),
                            relativeSeq1Range.move(-shift),
                            translationParameters,
                            isIncludeFirstInserts
                        )
                    }
                }
            }
        }

        private fun MutationsSet.mutationsOfCDR3(): Mutations<NucleotideSequence> =
            VMutations.partInCDR3.mutations.move(-rootInfo.VRangeInCDR3.lower)
                .concat(NDNMutations.mutations.move(rootInfo.VRangeInCDR3.length()))
                .concat(
                    JMutations.partInCDR3.mutations
                        .move(rootInfo.VRangeInCDR3.length() + rootInfo.reconstructedNDN.size() - rootInfo.JRangeInCDR3.lower)
                )

        private fun partitioning(geneType: GeneType): ReferencePoints =
            geneSupplier(rootInfo.VJBase.getGeneId(geneType)).partitioning
    }

    @Suppress("EnumEntryName")
    enum class Base {
        root,
        mrca,
        parent
    }
}

fun SHMTreeResult.forPostanalysis(
    assemblerParameters: CloneAssemblerParameters,
    alignerParameters: VDJCAlignerParameters,
    libraryRegistry: VDJCLibraryRegistry
): SHMTreeForPostanalysis = SHMTreeForPostanalysis(
    tree.map { parent, node, distance ->
        SHMTreeForPostanalysis.Node(
            node,
            parent,
            mostRecentCommonAncestor,
            distance,
            rootInfo,
            assemblerParameters,
            alignerParameters
        ) { geneId -> libraryRegistry.getGene(geneId) }
    },
    rootInfo,
    treeId
)
