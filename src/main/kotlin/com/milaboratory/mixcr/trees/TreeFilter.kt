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

import com.milaboratory.core.sequence.Sequence
import com.milaboratory.mixcr.postanalysis.plots.SeqPattern
import io.repseq.core.Chains
import io.repseq.core.GeneType

class TreeFilter(
    /** minimal number of nodes in tree */
    val minNodes: Int? = null,
    /** minimal height of the tree */
    val minHeight: Int? = null,
    /** filter specific trees by id */
    val treeIds: Set<Int>? = null,
    /** filter specific trees by pattern */
    val seqPattern: SeqPattern? = null,
    /** only trees containing clones with specified chain */
    val chains: Chains? = null,
) {
    fun match(treeId: Int): Boolean = treeIds?.contains(treeId) != false

    fun match(tree: SHMTreeForPostanalysis<*>): Boolean {
        if (minNodes != null && tree.tree.allNodes().count() < minNodes)
            return false
        if (minHeight != null && tree.tree.root.height() < minHeight)
            return false
        if (treeIds != null && !treeIds.contains(tree.meta.treeId))
            return false
        if (seqPattern != null) {
            return tree.tree.allNodes()
                .asSequence()
                .map { it.node.content }
                .any { node ->
                    val sequence: Sequence<*>? = if (seqPattern.isAA)
                        node.mutationsFromGermline().targetAASequence(seqPattern.feature)
                    else
                        node.mutationsFromGermline().targetNSequence(seqPattern.feature)
                    if (sequence == null) return false
                    seqPattern.bitapQ(sequence)
                }
        }
        if (chains != null) {
            return tree.tree.allNodes()
                .asSequence()
                .flatMap { it.node.content.clones }
                .any { (clone) ->
                    GeneType.VJC_REFERENCE.any { gt ->
                        val bestHit = clone.getBestHit(gt)
                        bestHit != null && chains.intersects(bestHit.gene.chains)
                    }
                }
        }
        return true
    }
}
