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
import io.repseq.core.*
import java.math.BigDecimal

data class SHMTreeForPostanalysis(
    val tree: Tree<NodeWithClones>,
    val meta: Meta
) {
    class Meta(
        val rootInfo: RootInfo,
        val treeId: Int,
        val fileNames: List<String>,
        val root: MutationsSet,
        val mostRecentCommonAncestor: MutationsSet,
        private val assemblerParameters: CloneAssemblerParameters,
        private val alignerParameters: VDJCAlignerParameters,
        private val geneSupplier: (VDJCGeneId) -> VDJCGene
    ) {
        fun partitioning(geneType: GeneType): ReferencePoints =
            geneSupplier(rootInfo.VJBase.getGeneId(geneType)).partitioning

        fun geneFeatureToAlign(geneType: GeneType): GeneFeature =
            alignerParameters.getGeneAlignerParameters(geneType).geneFeatureToAlign

        fun assemblingFeaturesContains(geneFeature: GeneFeature) =
            assemblerParameters.assemblingFeatures.any { geneFeature in it }
    }


    class NodeWithClones(
        id: Int,
        meta: Meta,
        main: MutationsSet,
        parent: MutationsSet?,
        distanceFromRoot: BigDecimal,
        distanceFromMostRecentCommonAncestor: BigDecimal?,
        distanceFromParent: BigDecimal?,
        val clones: List<CloneWithDatasetId>
    ) : BaseNode(id, meta, main, parent, distanceFromRoot, distanceFromMostRecentCommonAncestor, distanceFromParent) {
        fun split(): Collection<SplittedNode> = when {
            clones.isEmpty() -> listOf(withClone(null))
            else -> clones.map { clone -> withClone(clone) }
        }

        private fun withClone(clone: CloneWithDatasetId?) = SplittedNode(
            id,
            meta,
            main = main,
            parent = parent,
            distanceFromRoot = distanceFromRoot,
            distanceFromMostRecentCommonAncestor = distanceFromMostRecentCommonAncestor,
            distanceFromParent = distanceFromParent,
            clone
        )
    }

    class SplittedNode(
        id: Int,
        meta: Meta,
        main: MutationsSet,
        parent: MutationsSet?,
        distanceFromRoot: BigDecimal,
        distanceFromMostRecentCommonAncestor: BigDecimal?,
        distanceFromParent: BigDecimal?,
        val clone: CloneWithDatasetId?
    ) : BaseNode(id, meta, main, parent, distanceFromRoot, distanceFromMostRecentCommonAncestor, distanceFromParent)

    sealed class BaseNode(
        val id: Int,
        protected val meta: Meta,
        protected val main: MutationsSet,
        protected val parent: MutationsSet?,
        protected val distanceFromRoot: BigDecimal,
        protected val distanceFromMostRecentCommonAncestor: BigDecimal?,
        val distanceFromParent: BigDecimal?,
    ) {
        fun distanceFrom(base: Base): BigDecimal? = when (base) {
            Base.root -> distanceFromRoot
            Base.mrca -> distanceFromMostRecentCommonAncestor
            Base.parent -> distanceFromParent
        }

        fun mutationsDescription(
            geneFeature: GeneFeature,
            relativeTo: GeneFeature? = null,
            baseOn: Base = Base.root
        ): MutationsDescription? {
            if (!canExtractMutations(geneFeature, relativeTo)) return null
            val mainMutations = main.mutationsDescription(geneFeature, relativeTo)
            return when (baseOn) {
                Base.root -> mainMutations
                Base.mrca -> mainMutations.differenceWith(
                    meta.mostRecentCommonAncestor.mutationsDescription(geneFeature, relativeTo)
                )
                Base.parent -> when (parent) {
                    null -> null
                    else -> mainMutations.differenceWith(
                        parent.mutationsDescription(geneFeature, relativeTo)
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
                    !meta.assemblingFeaturesContains(geneFeature) -> false
                    else -> geneFeature.geneType != null
                }
            }

        //TODO work with all geneFeatures, for example Exon2 and JRegionTrimmed
        private fun MutationsSet.mutationsDescription(
            geneFeature: GeneFeature,
            relativeTo: GeneFeature?
        ): MutationsDescription = when (geneFeature) {
            GeneFeature.CDR3 -> {
                require(relativeTo == null)
                val baseCDR3 = meta.rootInfo.baseCDR3()
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
                val geneFeatureToAlign = meta.geneFeatureToAlign(geneType)
                val partitioning = meta.partitioning(geneType)
                val relativeSeq1Range = Range(
                    partitioning.getRelativePosition(geneFeatureToAlign, geneFeature.firstPoint),
                    partitioning.getRelativePosition(geneFeatureToAlign, geneFeature.lastPoint)
                )
                val mutations = getGeneMutations(geneType).combinedMutations()
                val isIncludeFirstInserts = geneFeatureToAlign.firstPoint == geneFeature.firstPoint
                val translationParameters = partitioning.getTranslationParameters(geneFeature)
                when (relativeTo) {
                    null -> MutationsDescription(
                        meta.rootInfo.getSequence1(geneType),
                        mutations,
                        relativeSeq1Range,
                        translationParameters,
                        isIncludeFirstInserts
                    )
                    else -> {
                        val shift = partitioning.getRelativePosition(geneFeature, relativeTo.firstPoint)
                        val sequence1 = meta.rootInfo.getSequence1(geneType)
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
            VMutations.partInCDR3.mutations.move(-meta.rootInfo.VRangeInCDR3.lower)
                .concat(NDNMutations.mutations.move(meta.rootInfo.VRangeInCDR3.length()))
                .concat(
                    JMutations.partInCDR3.mutations
                        .move(meta.rootInfo.VRangeInCDR3.length() + meta.rootInfo.reconstructedNDN.size() - meta.rootInfo.JRangeInCDR3.lower)
                )
    }

    class CloneWithDatasetId(
        val clone: Clone,
        val datasetId: Int,
        val fileName: String
    )

    @Suppress("EnumEntryName")
    enum class Base {
        root,
        mrca,
        parent
    }
}

fun SHMTreeResult.forPostanalysis(
    fileNames: List<String>,
    assemblerParameters: CloneAssemblerParameters,
    alignerParameters: VDJCAlignerParameters,
    libraryRegistry: VDJCLibraryRegistry
): SHMTreeForPostanalysis {
    val meta = SHMTreeForPostanalysis.Meta(
        rootInfo,
        treeId,
        fileNames,
        tree.root.content.mutationsSet,
        mostRecentCommonAncestor.mutationsSet,
        assemblerParameters,
        alignerParameters
    ) { geneId -> libraryRegistry.getGene(geneId) }

    val mappedRoot = tree.root.map(meta, null)

    return SHMTreeForPostanalysis(
        Tree(mappedRoot),
        meta
    )
}

private fun Tree.Node<CloneOrFoundAncestor>.map(
    meta: SHMTreeForPostanalysis.Meta,
    parent: Tree.Node<CloneOrFoundAncestor>?
): Tree.Node<SHMTreeForPostanalysis.NodeWithClones> {
    val distanceFromRoot: BigDecimal
    val distanceFromMostRecentCommonAncestor: BigDecimal?
    val distanceFromParent: BigDecimal?
    if (parent == null) {
        distanceFromRoot = BigDecimal.ZERO
        distanceFromMostRecentCommonAncestor = null
        distanceFromParent = null
    } else {
        distanceFromRoot = distance(meta.rootInfo, meta.root, content.mutationsSet)
        distanceFromMostRecentCommonAncestor = distance(
            meta.rootInfo,
            meta.mostRecentCommonAncestor,
            content.mutationsSet
        )
        distanceFromParent = distance(meta.rootInfo, parent.content.mutationsSet, content.mutationsSet)
    }

    return Tree.Node(
        SHMTreeForPostanalysis.NodeWithClones(
            content.id,
            meta,
            main = content.mutationsSet,
            parent = parent?.content?.mutationsSet,
            distanceFromRoot = distanceFromRoot,
            distanceFromMostRecentCommonAncestor = distanceFromMostRecentCommonAncestor,
            distanceFromParent = distanceFromParent,
            links.filter { it.distance.compareTo(BigDecimal.ZERO) == 0 }
                .map {
                    SHMTreeForPostanalysis.CloneWithDatasetId(
                        it.node.content.clone!!,
                        it.node.content.datasetId!!,
                        meta.fileNames[it.node.content.datasetId]
                    )
                }
        ),
        links.filter { it.distance.compareTo(BigDecimal.ZERO) != 0 }
            .map {
                require(it.node.content.clone == null)
                val mappedChild = it.node.map(meta, this)
                Tree.NodeLink(mappedChild, mappedChild.content.distanceFromParent!!)
            }
    )
}

fun distance(rootInfo: RootInfo, from: MutationsSet, to: MutationsSet): BigDecimal {
    val mutationsBetween = MutationsUtils.mutationsBetween(rootInfo, from, to)
    val mutationsCount = mutationsBetween.VMutationsWithoutCDR3.values.sumOf { it.mutations.size() } +
            mutationsBetween.JMutationsInCDR3WithoutNDN.mutations.size() +
            mutationsBetween.knownNDN.mutations.size() +
            mutationsBetween.JMutationsInCDR3WithoutNDN.mutations.size() +
            mutationsBetween.JMutationsWithoutCDR3.values.sumOf { it.mutations.size() }
    val sequence1Length = from.VMutations.sequence1Length() +
            rootInfo.reconstructedNDN.size() +
            from.JMutations.sequence1Length()
    return BigDecimal.valueOf(mutationsCount / sequence1Length.toDouble())
}
