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

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.cli.AbstractCommandReportBuilder
import com.milaboratory.mixcr.cli.AbstractMiXCRCommandReport
import com.milaboratory.mixcr.cli.CommandFindShmTrees
import com.milaboratory.mixcr.cli.MiXCRCommandReport.StatsWithQuantiles
import com.milaboratory.mixcr.util.VJPair
import com.milaboratory.mixcr.util.XSV
import com.milaboratory.util.ReportHelper
import io.repseq.core.VDJCGene
import java.io.File
import java.util.*
import kotlin.math.log2

@JsonAutoDetect(
    fieldVisibility = ANY,
    isGetterVisibility = NONE,
    getterVisibility = NONE
)
class BuildSHMTreeReport(
    date: Date?,
    commandLine: String,
    inputFiles: Array<String>,
    outputFiles: Array<String>,
    executionTimeMillis: Long?,
    version: String,
    @field:JsonProperty("stepResults")
    val stepResults: List<StepResult>,
    @field:JsonProperty("totalClonesProcessed")
    val totalClonesProcessed: Int? = null
) : AbstractMiXCRCommandReport(date, commandLine, inputFiles, outputFiles, executionTimeMillis, version) {
    override fun command(): String = CommandFindShmTrees.COMMAND_NAME

    override fun toString(): String = asString()

    override fun writeReport(helper: ReportHelper) {
        // Writing common analysis information
        writeSuperReport(helper)

        for (i in stepResults.indices) {
            val stepResult = stepResults[i]
            helper.writeField("Step ${i + 1}", stepResult.step.forPrint)
            if (stepResult.step !is BuildSHMTreeStep.CombineTrees) {
                helper.writeField("\tClones was added", stepResult.clonesWasAdded)
            }
            if (stepResult.step is BuildSHMTreeStep.BuildingInitialTrees) {
                helper.writeField("\tTrees created", stepResult.treesCountDelta)
            } else if (stepResult.step is BuildSHMTreeStep.CombineTrees) {
                helper.writeField("\tTrees combined", -stepResult.treesCountDelta)
            }
        }
        helper.writeField("Total trees count", totalTreesCount())
        helper.writeField("Total clones count in trees", totalClonesCountInTrees())
    }

    fun totalTreesCount() = stepResults.sumOf { it.treesCountDelta }

    fun totalClonesCountInTrees() = stepResults.sumOf { it.clonesWasAdded }

    @Suppress("unused")
    @JsonAutoDetect(
        fieldVisibility = ANY,
        isGetterVisibility = NONE,
        getterVisibility = NONE
    )
    @JsonIgnoreProperties(
        "mutationsRateDifferences",
        "minMutationsRateDifferences",
        "maxMutationsRateDifferences",
    )
    class StepResult(
        val step: BuildSHMTreeStep,
        val clonesWasAdded: Int,
        val cloneNodesWasAdded: Int,
        val treesCountDelta: Int,
        val commonVJMutationsCounts: StatsWithQuantiles,
        val clonesCountInTrees: StatsWithQuantiles,
        val wildcardsScore: StatsWithQuantiles,
        val wildcardsScoreForRoots: StatsWithQuantiles,
        val maxNDNsWildcardsScoreInTree: StatsWithQuantiles,
        val surenessOfDecisions: StatsWithQuantiles
    )

    class Builder : AbstractCommandReportBuilder<Builder>() {
        private val stepResults = mutableListOf<StepResult>()
        var totalClonesProcessed = -1

        override fun buildReport(): BuildSHMTreeReport {
            check(totalClonesProcessed != -1)
            return BuildSHMTreeReport(
                date,
                commandLine,
                inputFiles,
                outputFiles,
                executionTimeMillis,
                version,
                stepResults,
                totalClonesProcessed
            )
        }

        override fun that(): Builder = this

        fun addStatsForStep(
            step: BuildSHMTreeStep,
            stepResultDebug: SHMTreeBuilderOrchestrator.Debug,
            previousStepResultDebug: SHMTreeBuilderOrchestrator.Debug?,
            genes: Map<String, VDJCGene>
        ) {
            stepResults += calculateStatsFromDebug(
                step,
                stepResultDebug.treesBeforeDecisionsFile.parseDebug(genes),
                stepResultDebug.treesAfterDecisionsFile.parseDebug(genes),
                previousStepResultDebug?.treesAfterDecisionsFile?.parseDebug(genes)
            )
        }

        private fun File.parseDebug(genes: Map<String, VDJCGene>): List<Pair<TreeId, List<Pair<Int, Map<String, String?>>>>> =
            XSV.readXSV(this, DebugInfo.COLUMNS_FOR_XSV.keys, ";")
                .asSequence()
                .map { row ->
                    val treeId = TreeId(
                        row["treeId"]!!.toInt(),
                        VJBase(
                            VJPair(
                                genes[row["VGeneName"]!!]!!.id,
                                genes[row["JGeneName"]!!]!!.id
                            ),
                            row["CDR3Length"]!!.toInt()
                        )
                    )
                    val nodeId = row["id"]!!.toInt()
                    treeId to (nodeId to row - arrayOf("treeId", "treeIdFull", "VGeneName", "JGeneName", "id"))
                }
                .groupBy({ (treeId) -> treeId }, { (_, node) -> node })
                .toList()
                .map { (treeId, nodes) -> treeId to nodes.sortedBy { it.first } }
                .sortedWith(Comparator.comparing({ it.first }, TreeId.comparator))
                .toList()

        private fun calculateStatsFromDebug(
            step: BuildSHMTreeStep,
            debugInfosBeforeDecisions: List<Pair<TreeId, List<Pair<Int, Map<String, String?>>>>>,
            debugInfosAfterDecisions: List<Pair<TreeId, List<Pair<Int, Map<String, String?>>>>>,
            previousStepDebug: List<Pair<TreeId, List<Pair<Int, Map<String, String?>>>>>?
        ): StepResult {
            val clonesWasAdded = debugInfosAfterDecisions.clonesCount - (previousStepDebug?.clonesCount ?: 0)
            val cloneNodesWasAdded =
                debugInfosAfterDecisions.cloneNodesCount - (previousStepDebug?.cloneNodesCount ?: 0)
            val treesCountDelta = debugInfosAfterDecisions.treesCount - (previousStepDebug?.treesCount ?: 0)

            val commonVJMutationsCounts = debugInfosAfterDecisions
                .map { (_, nodes) -> nodes.map { (_, row) -> row }.first { it["parentId"] == "0" } }
                .map { row ->
                    row.getMutations("VMutationsFromRoot").size() + row.getMutations("JMutationsFromRoot").size()
                }
            val clonesCountInTrees = debugInfosAfterDecisions
                .map { (_, nodes) ->
                    nodes.map { (_, row) -> row }
                        .filterNot { it["clonesIds"].isNullOrBlank() }
                        .flatMap { it["clonesIds"]!!.split(",") }
                        .count()
                }
            val NDNsByTrees = debugInfosAfterDecisions
                .map { (_, nodes) ->
                    nodes.filter { (nodeId) -> nodeId != 0 }
                        .map { (_, row) -> row.getNucleotideSequence("NDN") }
                }
            val averageNDNWildcardsScore = NDNsByTrees
                .map { NDNs ->
                    NDNs
                        .filter { it.size() != 0 }
                        .map { it.wildcardsScore() }
                        .average()
                }
            val NDNsWildcardsScoreForRoots = NDNsByTrees
                .map { it[0] }
                .filter { it.size() != 0 }
                .map { it.wildcardsScore() }
            val maxNDNsWildcardsScoreInTree = NDNsByTrees
                .mapNotNull { NDNs ->
                    NDNs
                        .filter { it.size() != 0 }
                        .maxOfOrNull { NDN -> NDN.wildcardsScore() }
                }
            val surenessOfDecisions = debugInfosBeforeDecisions
                .flatMap { (_, nodes) -> nodes.map { (_, row) -> row } }
                .filterNot { it["clonesIds"].isNullOrBlank() }
                .filter { it["decisionMetric"] != null }
                .flatMap { row -> row["clonesIds"]!!.split(",").map { it to row["decisionMetric"]!!.toDouble() } }
                .groupBy({ it.first }) { it.second }
                .filterValues { it.size > 1 }
                .mapValues { (_, metrics) ->
                    val minMetric = metrics.minOrNull()!!
                    val maxMetric = metrics.minOrNull()!!
                    (maxMetric - minMetric) / maxMetric
                }
                .values

            return StepResult(
                step = step,
                clonesWasAdded = clonesWasAdded,
                cloneNodesWasAdded = cloneNodesWasAdded,
                treesCountDelta = treesCountDelta,
                commonVJMutationsCounts = StatsWithQuantiles.from(commonVJMutationsCounts.map { it.toDouble() }),
                clonesCountInTrees = StatsWithQuantiles.from(clonesCountInTrees.map { it.toDouble() }),
                wildcardsScore = StatsWithQuantiles.from(averageNDNWildcardsScore),
                wildcardsScoreForRoots = StatsWithQuantiles.from(NDNsWildcardsScoreForRoots),
                maxNDNsWildcardsScoreInTree = StatsWithQuantiles.from(maxNDNsWildcardsScoreInTree),
                surenessOfDecisions = StatsWithQuantiles.from(surenessOfDecisions)
            )
        }

        private val List<Pair<TreeId, List<Pair<Int, Map<String, String?>>>>>.clonesCount
            get() = flatMap { it.second }
                .map { it.second["clonesCount"]!! }
                .sumOf { it.toInt() }

        private val List<Pair<TreeId, List<Pair<Int, Map<String, String?>>>>>.cloneNodesCount
            get() = flatMap { it.second }
                .mapNotNull { it.second["clonesIds"] }
                .count()

        private val List<Pair<TreeId, List<Pair<Int, Map<String, String?>>>>>.treesCount
            get() = size
    }
}

private fun Map<String, String?>.getNucleotideSequence(columnName: String): NucleotideSequence =
    if (this[columnName] != null) {
        NucleotideSequence(this[columnName])
    } else {
        NucleotideSequence.EMPTY
    }

private fun Map<String, String?>.getMutations(columnName: String): Mutations<NucleotideSequence> =
    if (this[columnName] != null) {
        Mutations(NucleotideSequence.ALPHABET, this[columnName])
    } else {
        Mutations.EMPTY_NUCLEOTIDE_MUTATIONS
    }

private fun NucleotideSequence.wildcardsScore(): Double = (0 until size())
    .map { NucleotideSequence.ALPHABET.codeToWildcard(codeAt(it)).basicSize() }
    .sumOf { log2(it.toDouble()) }
