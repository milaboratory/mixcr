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
import com.milaboratory.mixcr.util.VJPair
import com.milaboratory.primitivio.GroupingCriteria
import com.milaboratory.primitivio.PrimitivIOStateBuilder
import com.milaboratory.primitivio.groupBy
import com.milaboratory.primitivio.map
import com.milaboratory.primitivio.mapInParallel
import com.milaboratory.primitivio.mapNotNull
import com.milaboratory.util.TempFileDest
import io.repseq.core.GeneFeature
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.VDJCGeneId

class TreeBuilderByUserData(
    private val tempDest: TempFileDest,
    private val stateBuilder: PrimitivIOStateBuilder,
    private val geneFeatureToMatch: VJPair<GeneFeature>,
    private val threads: Int,
    private val assemblingFeatures: Array<GeneFeature>,
    private val SHMTreeBuilder: SHMTreeBuilder
) {
    fun buildByUserData(
        userInput: Map<CloneWrapper.ID, Int>,
        clonesWithDatasetIds: OutputPort<CloneWithDatasetId>
    ): OutputPort<TreeWithMetaBuilder> = clonesWithDatasetIds
        .combineWithUserData(userInput)
        .map { cluster ->
            val treeId = cluster.first().treeId
            asCloneWrappers(cluster, treeId) to treeId
        }
        .mapInParallel(threads) { (cluster, treeId) ->
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
            .filter { it.clone.ntLengthOf(CDR3, VGeneId, JGeneId) == VJBase.CDR3length }
            .filter { it.clone.coversFeature(geneFeatureToMatch, VJBase) }
            //filter compositions that not overlap with each another
            .filter { it.clone.formsAllRefPointsInCDR3(VJBase) }
            .map { CloneWrapper(it.clone, it.datasetId, VJBase, listOf(VJBase)) }

        if (cloneWrappers.size != cluster.size) {
            val excludedCloneIds = cluster.map { it.clone.id } - cloneWrappers.map { it.clone.id }.toSet()
            println("WARN: $excludedCloneIds will be not included in $treeId")
        }
        return cloneWrappers
    }

    private fun OutputPort<CloneWithDatasetId>.combineWithUserData(
        userInput: Map<CloneWrapper.ID, Int>
    ): OutputPort<List<CloneFromUserInput>> =
        mapNotNull { (clone, datasetId) ->
            val datasetIdWithCloneId = CloneWrapper.ID(datasetId = datasetId, cloneId = clone.id)
            val treeId = userInput[datasetIdWithCloneId] ?: return@mapNotNull null
            CloneFromUserInput(
                clone = clone,
                treeId = treeId,
                datasetId = datasetId
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
