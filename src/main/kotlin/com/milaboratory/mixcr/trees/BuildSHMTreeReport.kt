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
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.cli.AbstractCommandReportBuilder
import com.milaboratory.mixcr.cli.AbstractMiXCRCommandReport
import com.milaboratory.mixcr.cli.CommandFindShmTrees
import com.milaboratory.mixcr.cli.MiXCRCommandReport.StatsWithQuantiles
import com.milaboratory.mixcr.util.XSV
import com.milaboratory.util.ReportHelper
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
    val stepResults: List<StepResult>
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
        helper.writeField("Total trees count", stepResults.sumOf { it.treesCountDelta })
        helper.writeField("Total clones count in trees", stepResults.sumOf { it.clonesWasAdded })
    }

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
        override fun buildReport() = BuildSHMTreeReport(
            date, commandLine, inputFiles, outputFiles, executionTimeMillis, version, stepResults
        )

        override fun that(): Builder = this

        fun addStatsForStep(
            step: BuildSHMTreeStep,
            stepResultDebug: SHMTreeBuilderOrchestrator.Debug,
            previousStepResultDebug: SHMTreeBuilderOrchestrator.Debug?
        ) {
            stepResults += calculateStatsFromDebug(
                step,
                XSV.readXSV(stepResultDebug.treesBeforeDecisionsFile, DebugInfo.COLUMNS_FOR_XSV.keys, ";"),
                XSV.readXSV(stepResultDebug.treesAfterDecisionsFile, DebugInfo.COLUMNS_FOR_XSV.keys, ";"),
                previousStepResultDebug?.let {
                    XSV.readXSV(it.treesAfterDecisionsFile, DebugInfo.COLUMNS_FOR_XSV.keys, ";")
                }
            )
        }

        private fun calculateStatsFromDebug(
            step: BuildSHMTreeStep,
            debugInfosBeforeDecisions: List<Map<String, String?>>,
            debugInfosAfterDecisions: List<Map<String, String?>>,
            previousStepDebug: List<Map<String, String?>>?
        ): StepResult {
            val clonesWasAdded = debugInfosAfterDecisions.clonesCount - (previousStepDebug?.clonesCount ?: 0)
            val cloneNodesWasAdded =
                debugInfosAfterDecisions.cloneNodesCount - (previousStepDebug?.cloneNodesCount ?: 0)
            val treesCountDelta = debugInfosAfterDecisions.treesCount - (previousStepDebug?.treesCount ?: 0)

            val commonVJMutationsCounts = debugInfosAfterDecisions
                .filter { it["parentId"] == "0" }
                .map {
                    it.getMutations("VMutationsFromRoot").size() + it.getMutations("JMutationsFromRoot").size()
                }
            val clonesCountInTrees = debugInfosAfterDecisions
                .filterNot { it["clonesIds"].isNullOrBlank() }
                .groupBy({ it.treeId() }, { it["clonesIds"]?.split(",") ?: emptyList() })
                .values
                .map { it.size }
            val NDNsByTrees = debugInfosAfterDecisions
                .filter { it["id"] != "0" }
                .groupBy { it.treeId() }
                .mapValues { (_, value) ->
                    value.sortedBy { it["id"]!!.toInt() }.map { it.getNucleotideSequence("NDN") }
                }
            val averageNDNWildcardsScore = NDNsByTrees.values
                .map { NDNs ->
                    NDNs
                        .filter { it.size() != 0 }
                        .map { it.wildcardsScore() }
                        .average()
                }
            val NDNsWildcardsScoreForRoots = NDNsByTrees.values
                .map { it[0] }
                .filter { it.size() != 0 }
                .map { it.wildcardsScore() }
            val maxNDNsWildcardsScoreInTree = NDNsByTrees.values
                .mapNotNull { NDNs ->
                    NDNs
                        .filter { it.size() != 0 }
                        .maxOfOrNull { NDN -> NDN.wildcardsScore() }
                }
            val surenessOfDecisions = debugInfosBeforeDecisions
                .filterNot { it["clonesIds"].isNullOrBlank() }
                .filter { it["decisionMetric"] != null }
                .groupBy({ it["clonesIds"] }) { it["decisionMetric"]!!.toDouble() }
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

        private val List<Map<String, String?>>.clonesCount
            get() = map { it["clonesCount"]!! }
                .sumOf { it.toInt() }

        private val List<Map<String, String?>>.cloneNodesCount
            get() = mapNotNull { it["clonesIds"] }
                .count()

        private val List<Map<String, String?>>.treesCount
            get() = map { it["treeId"] }
                .distinct()
                .count()
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

private fun Map<String, String?>.treeId(): String =
    this["VGeneName"] + this["JGeneName"] + this["CDR3Length"] + this["treeId"]

private fun NucleotideSequence.wildcardsScore(): Double = (0 until size())
    .map { NucleotideSequence.ALPHABET.codeToWildcard(codeAt(it)).basicSize() }
    .sumOf { log2(it.toDouble()) }
