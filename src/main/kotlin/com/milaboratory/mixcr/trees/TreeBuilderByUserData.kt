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
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.GeneFeatures
import com.milaboratory.mixcr.util.VJPair
import com.milaboratory.primitivio.GroupingCriteria
import com.milaboratory.primitivio.PrimitivIOStateBuilder
import com.milaboratory.primitivio.groupBy
import com.milaboratory.primitivio.map
import com.milaboratory.primitivio.mapInParallelOrdered
import com.milaboratory.primitivio.mapNotNull
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
        .mapInParallelOrdered(threads) { (cluster, treeId) ->
            val rebasedFromGermline = cluster
                .map { cloneWrapper -> cloneWrapper.rebaseFromGermline(assemblingFeatures) }
            val result = SHMTreeBuilder.buildATreeFromRoot(rebasedFromGermline)
            result.copy(treeId = result.treeId.copy(id = treeId))
        }

    private fun asCloneWrappers(
        cluster: List<CloneFromUserInput>,
        treeId: Int
    ): List<CloneWrapper> {
        val VGeneId = cluster.map { it.clone }.bestGeneForClones(Variable)
        val JGeneId = cluster.map { it.clone }.bestGeneForClones(Joining)

        val CDR3lengths = cluster.map { it.clone.ntLengthOf(CDR3, VGeneId, JGeneId) }
            .groupingBy { it }.eachCount()
        if (CDR3lengths.size > 1) {
            println("WARN: in $treeId not all clones have the same length of CDR3")
        }
        val VJBase = VJBase(
            VJPair(V = VGeneId, J = JGeneId),
            CDR3length = CDR3lengths.values.first()
        )

        val cloneWrappers = cluster
            .asSequence()
            .filter { it.clone.ntLengthOf(CDR3, VGeneId, JGeneId) == VJBase.CDR3length }
            //filter compositions that not overlap with each another
            .filter { it.clone.formsAllRefPointsInCDR3(VJBase) }
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

        if (cloneWrappers.size != cluster.size) {
            val excludedCloneIds = cluster.map { it.clone.id } - cloneWrappers.map { it.id }.toSet()
            println("WARN: $excludedCloneIds will be not included in $treeId")
        }
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
        }.groupBy(
            stateBuilder,
            tempDest.addSuffix("tree.builder.userInput"),
            GroupingCriteria.groupBy { it.treeId }
        )

    private fun List<Clone>.bestGeneForClones(geneType: GeneType): VDJCGeneId =
        flatMap { clone -> clone.getHits(geneType).map { it.gene.id } }
            .groupingBy { it }.eachCount()
            .maxByOrNull { it.value }!!
            .key
}
