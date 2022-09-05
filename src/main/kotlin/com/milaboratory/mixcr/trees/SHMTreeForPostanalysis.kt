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
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.ReferencePoint.CDR3Begin
import io.repseq.core.ReferencePoint.CDR3End
import io.repseq.core.ReferencePoint.JBeginTrimmed
import io.repseq.core.ReferencePoint.VEndTrimmed
import io.repseq.core.ReferencePoints
import io.repseq.core.VDJCGene
import io.repseq.core.VDJCGeneId
import io.repseq.core.VDJCLibraryRegistry
import java.util.*

data class SHMTreeForPostanalysis(
    val tree: Tree<NodeWithClones>,
    val meta: Meta,
    val root: MutationsDescription,
    val mrca: MutationsDescription
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

        fun getGene(geneType: GeneType) = geneSupplier(rootInfo.VJBase.geneIds[geneType])

        fun geneFeatureToAlign(geneType: GeneType): GeneFeature =
            alignerParameters.getGeneAlignerParameters(geneType).geneFeatureToAlign
    }


    class NodeWithClones(
        id: Int,
        parentId: Int?,
        meta: Meta,
        root: MutationsDescription,
        mostRecentCommonAncestor: MutationsDescription,
        main: MutationsDescription,
        parent: MutationsDescription?,
        val clones: List<CloneWithDatasetId>
    ) : BaseNode(
        id = id,
        parentId = parentId,
        meta = meta,
        root = root,
        mostRecentCommonAncestor = mostRecentCommonAncestor,
        main = main,
        parent = parent,
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
            root = root,
            mostRecentCommonAncestor = mostRecentCommonAncestor,
            parent = parent,
            clone = clone
        )

        private fun withoutClone() = SplittedNode(
            id = id,
            parentId = parentId,
            meta = meta,
            main = main,
            root = root,
            mostRecentCommonAncestor = mostRecentCommonAncestor,
            parent = parent,
            clone = null
        )
    }

    class SplittedNode(
        id: Int,
        parentId: Int?,
        meta: Meta,
        root: MutationsDescription,
        mostRecentCommonAncestor: MutationsDescription,
        main: MutationsDescription,
        parent: MutationsDescription?,
        val clone: CloneWithDatasetId?
    ) : BaseNode(
        id = id,
        parentId = parentId,
        root = root,
        meta = meta,
        mostRecentCommonAncestor = mostRecentCommonAncestor,
        main = main,
        parent = parent,
    )

    sealed class BaseNode(
        val id: Int,
        val parentId: Int?,
        protected val meta: Meta,
        protected val mostRecentCommonAncestor: MutationsDescription,
        protected val root: MutationsDescription,
        protected val main: MutationsDescription,
        protected val parent: MutationsDescription?,
    ) {
        fun distanceFrom(base: Base): Double? = when (base) {
            Base.germline -> main.distanceFrom(root)
            Base.mrca -> when (parent) {
                null -> null
                else -> main.distanceFrom(mostRecentCommonAncestor)
            }
            Base.parent -> parent?.let { main.distanceFrom(parent) }
        }

        fun mutationsFromGermline(): MutationsDescription = main
        fun mutationsFromGermlineTo(base: Base?): MutationsDescription? = when (base) {
            Base.germline -> root
            Base.mrca -> mostRecentCommonAncestor
            Base.parent -> parent
            null -> main
        }

        fun mutationsFrom(baseOn: Base): MutationsDescription? = when (baseOn) {
            Base.germline -> main
            Base.mrca -> mostRecentCommonAncestor.differenceWith(main)
            Base.parent -> parent?.differenceWith(main)
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

    val root = tree.root.content.mutationsSet.asMutationsDescription(meta)
    val mrca = mostRecentCommonAncestor.mutationsSet.asMutationsDescription(meta)
    val mappedRoot = tree.root.map(
        meta,
        null,
        root,
        mrca
    )

    return SHMTreeForPostanalysis(
        Tree(mappedRoot.first),
        meta,
        root,
        mrca
    )
}

private fun Tree.Node<CloneOrFoundAncestor>.map(
    meta: SHMTreeForPostanalysis.Meta,
    parent: Tree.Node<CloneOrFoundAncestor>?,
    root: MutationsDescription,
    mrca: MutationsDescription
): Pair<Tree.Node<SHMTreeForPostanalysis.NodeWithClones>, Double?> {
    val main = content.mutationsSet.asMutationsDescription(meta)
    val mappedParent = parent?.content?.mutationsSet?.asMutationsDescription(meta)
    val distanceFromParent = mappedParent?.let { main.distanceFrom(it) }
    val result = Tree.Node(
        SHMTreeForPostanalysis.NodeWithClones(
            content.id,
            parent?.content?.id,
            meta,
            main = main,
            root = root,
            mostRecentCommonAncestor = mrca,
            parent = mappedParent,
            clones = links.filter { it.distance == 0.0 }
                .flatMap { nodeLink ->
                    nodeLink.node.content.clones.map { (clone, datasetId) ->
                        SHMTreeForPostanalysis.CloneWithDatasetId(
                            clone,
                            datasetId,
                            meta.fileNames[datasetId]
                        )
                    }
                }
        )
    )
    links
        .filter { it.distance != 0.0 }
        .forEach {
            require(it.node.content.clones.isEmpty())
            val (mappedChild, fromParent) = it.node.map(meta, parent = this, root = root, mrca = mrca)
            result.addChild(mappedChild, fromParent!!)
        }
    return result to distanceFromParent
}

private fun MutationsSet.asMutationsDescription(meta: SHMTreeForPostanalysis.Meta): MutationsDescription =
    MutationsDescription(
        VPartsOfMutationsDescriptor(),
        meta.rootInfo.sequence1.V,
        meta.rootInfo.partitioning.V
            .withVCDR3PartLength(meta.rootInfo.rangeInCDR3.V.length()),
        meta.rootInfo.reconstructedNDN,
        NDNMutations.mutations,
        JPartsOfMutationsDescriptor(),
        meta.rootInfo.sequence1.J,
        meta.rootInfo.partitioning.J
            .withJCDR3PartLength(meta.rootInfo.rangeInCDR3.J.length())
    )

private fun MutationsSet.VPartsOfMutationsDescriptor(): SortedMap<GeneFeature, Mutations<NucleotideSequence>> =
    mutations.V.mutationsOutsideOfCDR3.entries.associateTo(TreeMap()) { (geneFeature, mutationsInFeature) ->
        if (geneFeature.lastPoint == CDR3Begin) {
            GeneFeature(geneFeature.firstPoint, VEndTrimmed) to
                    mutationsInFeature.concat(mutations.V.partInCDR3.mutations)
        } else {
            geneFeature to mutationsInFeature
        }
    }

private fun MutationsSet.JPartsOfMutationsDescriptor(): SortedMap<GeneFeature, Mutations<NucleotideSequence>> =
    mutations.J.mutationsOutsideOfCDR3.entries.associateTo(TreeMap()) { (geneFeature, mutationsInFeature) ->
        if (geneFeature.firstPoint == CDR3End) {
            GeneFeature(JBeginTrimmed, geneFeature.lastPoint) to
                    mutations.J.partInCDR3.mutations.concat(mutationsInFeature)
        } else {
            geneFeature to mutationsInFeature
        }
    }

fun MutationsDescription.distanceFrom(to: MutationsDescription): Double =
    differenceWith(to).nMutationsCount.toDouble()
