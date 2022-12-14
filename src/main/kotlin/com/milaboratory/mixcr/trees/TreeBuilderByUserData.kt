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
@file:Suppress("LocalVariableName")

package com.milaboratory.mixcr.trees

import cc.redberry.pipe.OutputPort
import cc.redberry.pipe.util.buffered
import cc.redberry.pipe.util.filter
import cc.redberry.pipe.util.map
import cc.redberry.pipe.util.mapInParallel
import cc.redberry.pipe.util.mapNotNull
import cc.redberry.pipe.util.ordered
import cc.redberry.pipe.util.toList
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.GeneFeatures
import com.milaboratory.mixcr.cli.logger
import com.milaboratory.mixcr.util.VJPair
import com.milaboratory.primitivio.PrimitivIOStateBuilder
import com.milaboratory.primitivio.groupByOnDisk
import com.milaboratory.util.TempFileDest
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.VDJCGeneId

class TreeBuilderByUserData(
    private val tempDest: TempFileDest,
    private val stateBuilder: PrimitivIOStateBuilder,
    private val assemblingFeatures: GeneFeatures,
    private val SHMTreeBuilder: SHMTreeBuilder
) {
    fun buildByUserData(
        clonesWithDatasetIds: OutputPort<CloneWithDatasetId>,
        userInput: Map<CloneWithDatasetId.ID, Int>,
        threads: Int
    ): OutputPort<TreeWithMetaBuilder> = clonesWithDatasetIds
        .combineWithUserData(userInput)
        .map { cluster ->
            val treeId = cluster.first().treeId
            asCloneWrappers(cluster, treeId) to treeId
        }
        .filter { it.first.isNotEmpty() }
        .buffered(1) //also make take() from upstream synchronized
        .mapInParallel(threads) { (cluster, treeId) ->
            val rebasedFromGermline = cluster
                .map { cloneWrapper -> cloneWrapper.rebaseFromGermline(assemblingFeatures) }
            val result = SHMTreeBuilder.buildATreeFromRoot(rebasedFromGermline)
            result.copy(treeId = result.treeId.copy(id = treeId))
        }
        .ordered { it.treeId.id.toLong() }

    private fun asCloneWrappers(
        cluster: List<CloneFromUserInput>,
        treeId: Int
    ): List<CloneWrapper> {
        val VGeneId = cluster.map { it.clone }.bestGeneForClones(Variable)
        val JGeneId = cluster.map { it.clone }.bestGeneForClones(Joining)

        val CDR3lengths = cluster.map { it.clone.ntLengthOf(CDR3, VGeneId, JGeneId) }
            .groupingBy { it }.eachCount()
        if (CDR3lengths.size > 1) {
            logger.warn("in $treeId not all clones have the same length of CDR3")
        }
        val VJBase = VJBase(
            VJPair(V = VGeneId, J = JGeneId),
            CDR3length = CDR3lengths.values.first()
        )

        val cloneWrappers = cluster
            .partition { it.clone.getFeature(CDR3, VJBase) != null }
            .also { (_, excluded) ->
                if (excluded.isNotEmpty()) {
                    logger.warn("${excluded.map { it.id }} have other genes than majority in tree $treeId")
                }
            }
            .first
            .partition { it.clone.ntLengthOf(CDR3, VGeneId, JGeneId) == VJBase.CDR3length }
            .also { (_, excluded) ->
                if (excluded.isNotEmpty()) {
                    logger.warn("${excluded.map { it.id }} have other CDR3length than majority in tree $treeId")
                }
            }
            .first
            //filter compositions that not overlap with each another
            .partition { it.clone.formsAllRefPointsInCDR3(VJBase) }
            .also { (_, excluded) ->
                if (excluded.isNotEmpty()) {
                    logger.warn("${excluded.map { it.id }} can not form correct CDR3 ($treeId)")
                }
            }
            .first
            .groupBy { it.clone.targets.toList() }
            .values
            .map { theSameClones ->
                CloneWrapper(
                    theSameClones.map { CloneWithDatasetId(it.clone, it.datasetId) },
                    VJBase,
                    listOf(VJBase)
                )
            }
            .toList()

        return cloneWrappers
    }

    private fun OutputPort<CloneWithDatasetId>.combineWithUserData(
        userInput: Map<CloneWithDatasetId.ID, Int>
    ): OutputPort<List<CloneFromUserInput>> =
        mapNotNull { cloneWithDatasetId ->
            val datasetIdWithCloneId = cloneWithDatasetId.id
            val treeId = userInput[datasetIdWithCloneId] ?: return@mapNotNull null
            CloneFromUserInput(
                clone = cloneWithDatasetId.clone,
                treeId = treeId,
                datasetId = cloneWithDatasetId.datasetId
            )
        }
            .groupByOnDisk(
                stateBuilder,
                tempDest.addSuffix("tree.builder.userInput")
            ) { it.treeId }
            .map { it.toList() }

    private fun List<Clone>.bestGeneForClones(geneType: GeneType): VDJCGeneId =
        flatMap { clone -> clone.getHits(geneType).map { it.gene.id } }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }!!
            .key
}
