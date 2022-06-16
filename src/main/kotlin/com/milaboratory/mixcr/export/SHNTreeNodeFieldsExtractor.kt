@file:Suppress("LocalVariableName")

package com.milaboratory.mixcr.export

import com.milaboratory.core.Range
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.MutationsUtil
import com.milaboratory.core.sequence.AminoAcidSequence
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.core.sequence.TranslationParameters.FromLeftWithIncompleteCodon
import com.milaboratory.mixcr.export.FieldExtractors.NULL
import com.milaboratory.mixcr.trees.MutationsUtils
import com.milaboratory.mixcr.util.extractAbsoluteMutations
import io.repseq.core.GeneFeature
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneType
import io.repseq.core.ReferencePoints

object SHNTreeNodeFieldsExtractor : BaseFieldExtractors() {
    override fun initFields(): Array<Field<out Any>> {
        val nodeFields = mutableListOf<Field<SHMTreeNodeToPrint>>()

        nodeFields += WithHeader(
            "-nFeature",
            "Export nucleotide sequence of specified gene feature",
            "N. Seq. ",
            "nSeq",
            validateArgs = ::checkNotComposite
        ) { node, geneFeature ->
            node.targetNSequence(geneFeature)?.toString() ?: NULL
        }

        nodeFields += WithHeader(
            "-aaFeature",
            "Export amino acid sequence of specified gene feature",
            "AA. Seq. ",
            "aaSeq",
            validateArgs = ::checkNotComposite
        ) { node, geneFeature ->
            val nSequence = node.targetNSequence(geneFeature) ?: return@WithHeader NULL
            val translationParameters = when (geneFeature) {
                CDR3 -> FromLeftWithIncompleteCodon
                else -> node.partitioning(geneFeature.geneType).getTranslationParameters(geneFeature)
            }
            AminoAcidSequence.translate(nSequence, translationParameters).toString()
        }

        nodeFields += WithHeader(
            //TODO
            "-aaFeatureMutations",
            "Export amino acid sequence of specified gene feature",
            "AA. Seq. ",
            "aaSeq",
            validateArgs = ::checkNotComposite
        ) { node, geneFeature ->
            val rootInfo = node.rootInfo
            if (geneFeature == CDR3) {
                val mutationsSet = node.cloneOrFoundAncestor.mutationsSet
                val CDR3Mutations = mutationsSet.VMutations.partInCDR3.mutations.move(-rootInfo.VRangeInCDR3.lower)
                    .concat(mutationsSet.NDNMutations.mutations.move(rootInfo.VRangeInCDR3.length()))
                    .concat(
                        mutationsSet.JMutations.partInCDR3.mutations.move(-rootInfo.JRangeInCDR3.lower)
                            .move(rootInfo.VRangeInCDR3.length() + rootInfo.VRangeInCDR3.length())
                    )
                MutationsUtil.nt2aaDetailed(
                    rootInfo.baseCDR3(),
                    CDR3Mutations,
                    FromLeftWithIncompleteCodon,
                    3
                )
            } else {
                if (node.assemblerParameters.assemblingFeatures.none { geneFeature in it }) return@WithHeader ""
                val geneType = geneFeature.geneType ?: return@WithHeader ""
                val partitioning = node.partitioning(geneType)
                val translationParameters = partitioning.getTranslationParameters(geneFeature)
                val (relativeRangeOfFeature, mutations) = node.mutationForGeneFeature(geneFeature)
                MutationsUtil.nt2aaDetailed(
                    rootInfo.getSequence1(geneType).getRange(relativeRangeOfFeature),
                    mutations.move(-relativeRangeOfFeature.lower),
                    translationParameters,
                    3
                )
            }.asString()
        }

        return nodeFields.toTypedArray()
    }

    private fun SHMTreeNodeToPrint.targetNSequence(geneFeature: GeneFeature): NucleotideSequence? =
        when (geneFeature) {
            CDR3 -> {
                val mutationsSet = cloneOrFoundAncestor.mutationsSet
                mutationsSet.VMutations.buildPartInCDR3(rootInfo)
                    .concatenate(mutationsSet.NDNMutations.buildSequence(rootInfo))
                    .concatenate(mutationsSet.JMutations.buildPartInCDR3(rootInfo))
            }
            else -> when {
                assemblerParameters.assemblingFeatures.none { geneFeature in it } -> null
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

    private fun checkNotComposite(it: GeneFeature) {
        if (it.isComposite) {
            throw IllegalArgumentException("Command doesn't support composite features")
        }
    }

}

private fun SHMTreeNodeToPrint.mutationForGeneFeature(geneFeature: GeneFeature): Pair<Range, Mutations<NucleotideSequence>> {
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

private fun relativeRangeOfFeature(
    partitioning: ReferencePoints,
    geneFeatureToAlign: GeneFeature,
    geneFeature: GeneFeature
): Range {
    val relativePositionOfFirst = partitioning.getRelativePosition(
        geneFeatureToAlign,
        geneFeature.firstPoint
    )
    val relativePositionOfLast = partitioning.getRelativePosition(
        geneFeatureToAlign,
        geneFeature.lastPoint
    )
    return Range(relativePositionOfFirst, relativePositionOfLast)
}

private fun SHMTreeNodeToPrint.geneFeatureToAlign(geneType: GeneType): GeneFeature =
    alignerParameters.getGeneAlignerParameters(geneType).geneFeatureToAlign

private fun SHMTreeNodeToPrint.partitioning(geneType: GeneType): ReferencePoints =
    geneSupplier(rootInfo.VJBase.getGeneId(geneType)).partitioning

private fun Array<MutationsUtil.MutationNt2AADescriptor>?.asString(): String = when (this) {
    null -> ""
    else -> joinToString { it.toString() }
}
