@file:Suppress("LocalVariableName")

package com.milaboratory.mixcr.export

import com.milaboratory.core.mutations.MutationsUtil
import com.milaboratory.core.sequence.AminoAcidSequence
import com.milaboratory.core.sequence.TranslationParameters.FromLeftWithIncompleteCodon
import com.milaboratory.mixcr.export.FieldExtractors.NULL
import io.repseq.core.GeneFeature
import io.repseq.core.GeneFeature.CDR3

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
                if (node.assemblingFeatures.none { geneFeature in it }) return@WithHeader ""
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

    private fun checkNotComposite(it: GeneFeature) {
        if (it.isComposite) {
            throw IllegalArgumentException("Command doesn't support composite features")
        }
    }

}

private fun Array<MutationsUtil.MutationNt2AADescriptor>?.asString(): String = when (this) {
    null -> ""
    else -> joinToString { it.toString() }
}
