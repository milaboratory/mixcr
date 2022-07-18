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
@file:Suppress("LocalVariableName", "FunctionName")

package com.milaboratory.mixcr.trees

import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import io.repseq.core.*
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.ReferencePoint.*
import java.math.BigDecimal
import java.util.*

data class SHMTreeForPostanalysis(
    val tree: Tree<NodeWithClones>,
    val meta: Meta
) {
    class Meta(
        val rootInfo: RootInfo,
        val treeId: Int,
        val fileNames: List<String>,
        private val alignerParameters: VDJCAlignerParameters,
        private val geneSupplier: (VDJCGeneId) -> VDJCGene
    ) {
        fun partitioning(geneType: GeneType): ReferencePoints =
            getGene(geneType).partitioning

        fun getGene(geneType: GeneType) = geneSupplier(rootInfo.VJBase.getGeneId(geneType))

        fun geneFeatureToAlign(geneType: GeneType): GeneFeature =
            alignerParameters.getGeneAlignerParameters(geneType).geneFeatureToAlign
    }


    class NodeWithClones(
        id: Int,
        parentId: Int?,
        meta: Meta,
        mostRecentCommonAncestor: MutationsDescription,
        main: MutationsDescription,
        parent: MutationsDescription?,
        distanceFromRoot: BigDecimal,
        distanceFromMostRecentCommonAncestor: BigDecimal?,
        distanceFromParent: BigDecimal?,
        val clones: List<CloneWithDatasetId>
    ) : BaseNode(
        id = id,
        parentId = parentId,
        meta = meta,
        mostRecentCommonAncestor = mostRecentCommonAncestor,
        main = main,
        parent = parent,
        distanceFromRoot = distanceFromRoot,
        distanceFromMostRecentCommonAncestor = distanceFromMostRecentCommonAncestor,
        distanceFromParent = distanceFromParent
    ) {
        fun split(): Collection<SplittedNode> = when {
            clones.isEmpty() -> listOf(withoutClone())
            else -> clones.map { clone -> withClone(clone) }
        }

        private fun withClone(clone: CloneWithDatasetId) = SplittedNode(
            id = id,
            parentId = parentId,
            meta = meta,
            main = main,
            mostRecentCommonAncestor = mostRecentCommonAncestor,
            parent = parent,
            distanceFromRoot = distanceFromRoot,
            distanceFromMostRecentCommonAncestor = distanceFromMostRecentCommonAncestor,
            distanceFromParent = distanceFromParent,
            clone = clone
        )

        private fun withoutClone() = SplittedNode(
            id = id,
            parentId = parentId,
            meta = meta,
            main = main,
            mostRecentCommonAncestor = mostRecentCommonAncestor,
            parent = parent,
            distanceFromRoot = distanceFromRoot,
            distanceFromMostRecentCommonAncestor = distanceFromMostRecentCommonAncestor,
            distanceFromParent = distanceFromParent,
            clone = null
        )
    }

    class SplittedNode(
        id: Int,
        parentId: Int?,
        meta: Meta,
        mostRecentCommonAncestor: MutationsDescription,
        main: MutationsDescription,
        parent: MutationsDescription?,
        distanceFromRoot: BigDecimal,
        distanceFromMostRecentCommonAncestor: BigDecimal?,
        distanceFromParent: BigDecimal?,
        val clone: CloneWithDatasetId?
    ) : BaseNode(
        id = id,
        parentId = parentId,
        meta = meta,
        mostRecentCommonAncestor = mostRecentCommonAncestor,
        main = main,
        parent = parent,
        distanceFromRoot = distanceFromRoot,
        distanceFromMostRecentCommonAncestor = distanceFromMostRecentCommonAncestor,
        distanceFromParent = distanceFromParent
    )

    sealed class BaseNode(
        val id: Int,
        val parentId: Int?,
        protected val meta: Meta,
        protected val mostRecentCommonAncestor: MutationsDescription,
        protected val main: MutationsDescription,
        protected val parent: MutationsDescription?,
        protected val distanceFromRoot: BigDecimal,
        protected val distanceFromMostRecentCommonAncestor: BigDecimal?,
        val distanceFromParent: BigDecimal?,
    ) {
        fun distanceFrom(base: Base): BigDecimal? = when (base) {
            Base.germline -> distanceFromRoot
            Base.mrca -> distanceFromMostRecentCommonAncestor
            Base.parent -> distanceFromParent
        }

        fun mutationsDescription() = main

        fun mutationsDescription(baseOn: Base): MutationsDescription? = when (baseOn) {
            Base.germline -> main
            Base.mrca -> main.differenceWith(mostRecentCommonAncestor)
            Base.parent -> parent?.let { main.differenceWith(it) }
        }
    }

    class CloneWithDatasetId(
        val clone: Clone,
        val datasetId: Int,
        val fileName: String
    )

    @Suppress("EnumEntryName")
    enum class Base {
        germline,
        mrca,
        parent
    }
}

fun SHMTreeResult.forPostanalysis(
    fileNames: List<String>,
    alignerParameters: VDJCAlignerParameters,
    libraryRegistry: VDJCLibraryRegistry
): SHMTreeForPostanalysis {
    val meta = SHMTreeForPostanalysis.Meta(
        rootInfo,
        treeId,
        fileNames,
        alignerParameters
    ) { geneId -> libraryRegistry.getGene(geneId) }

    val mappedRoot = tree.root.map(
        meta,
        null,
        tree.root.content.mutationsSet.asMutationsDescription(meta),
        mostRecentCommonAncestor.mutationsSet.asMutationsDescription(meta)
    )

    return SHMTreeForPostanalysis(
        Tree(mappedRoot),
        meta
    )
}

private fun Tree.Node<CloneOrFoundAncestor>.map(
    meta: SHMTreeForPostanalysis.Meta,
    parent: Tree.Node<CloneOrFoundAncestor>?,
    root: MutationsDescription,
    mrca: MutationsDescription
): Tree.Node<SHMTreeForPostanalysis.NodeWithClones> {
    val distanceFromRoot: BigDecimal
    val distanceFromMostRecentCommonAncestor: BigDecimal?
    val distanceFromParent: BigDecimal?
    val main = content.mutationsSet.asMutationsDescription(meta)
    val mappedParent = parent?.content?.mutationsSet?.asMutationsDescription(meta)
    if (parent == null) {
        distanceFromRoot = BigDecimal.ZERO
        distanceFromMostRecentCommonAncestor = null
        distanceFromParent = null
    } else {
        distanceFromRoot = root.distanceFrom(main)
        distanceFromMostRecentCommonAncestor = mrca.distanceFrom(main)
        distanceFromParent = mappedParent!!.distanceFrom(main)
    }
    val result = Tree.Node(
        SHMTreeForPostanalysis.NodeWithClones(
            content.id,
            parent?.content?.id,
            meta,
            main = main,
            mostRecentCommonAncestor = mrca,
            parent = mappedParent,
            distanceFromRoot = distanceFromRoot,
            distanceFromMostRecentCommonAncestor = distanceFromMostRecentCommonAncestor,
            distanceFromParent = distanceFromParent,
            clones = links.filter { it.distance.compareTo(BigDecimal.ZERO) == 0 }
                .map {
                    SHMTreeForPostanalysis.CloneWithDatasetId(
                        it.node.content.clone!!,
                        it.node.content.datasetId!!,
                        meta.fileNames[it.node.content.datasetId]
                    )
                }
        )
    )
    links
        .filter { it.distance.compareTo(BigDecimal.ZERO) != 0 }
        .forEach {
            require(it.node.content.clone == null)
            val mappedChild = it.node.map(meta, parent = this, root = root, mrca = mrca)
            result.addChild(mappedChild, mappedChild.content.distanceFromParent!!)
        }
    return result
}

private fun MutationsSet.asMutationsDescription(meta: SHMTreeForPostanalysis.Meta): MutationsDescription =
    MutationsDescription(
        VPartsOfMutationsDescriptor(),
        meta.rootInfo.VSequence,
        meta.rootInfo.getPartitioning(Variable)
            .withVCDR3PartLength(meta.rootInfo.VRangeInCDR3.length()),
        meta.rootInfo.reconstructedNDN,
        NDNMutations.mutations,
        JPartsOfMutationsDescriptor(),
        meta.rootInfo.JSequence,
        meta.rootInfo.getPartitioning(Joining)
            .withJCDR3PartLength(meta.rootInfo.JRangeInCDR3.length())
    )

private fun MutationsSet.VPartsOfMutationsDescriptor(): SortedMap<GeneFeature, Mutations<NucleotideSequence>> =
    VMutations.mutations.entries.associateTo(TreeMap()) { (geneFeature, mutations) ->
        if (geneFeature.lastPoint == CDR3Begin) {
            GeneFeature(geneFeature.firstPoint, VEndTrimmed) to
                    mutations.concat(VMutations.partInCDR3.mutations)
        } else {
            geneFeature to mutations
        }
    }

private fun MutationsSet.JPartsOfMutationsDescriptor(): SortedMap<GeneFeature, Mutations<NucleotideSequence>> =
    JMutations.mutations.entries.associateTo(TreeMap()) { (geneFeature, mutations) ->
        if (geneFeature.firstPoint == CDR3End) {
            GeneFeature(JBeginTrimmed, geneFeature.lastPoint) to
                    JMutations.partInCDR3.mutations.concat(mutations)
        } else {
            geneFeature to mutations
        }
    }

fun MutationsDescription.distanceFrom(to: MutationsDescription): BigDecimal =
    BigDecimal(differenceWith(to).nMutationsCount)
