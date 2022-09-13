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

package com.milaboratory.mixcr.cli

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.trees.BuildSHMTreeStep
import com.milaboratory.mixcr.trees.BuildSHMTreeStep.BuildingInitialTrees
import com.milaboratory.mixcr.trees.BuildSHMTreeStep.CombineTrees
import com.milaboratory.mixcr.trees.DebugInfo
import com.milaboratory.mixcr.trees.SHMTreeBuilderOrchestrator
import com.milaboratory.mixcr.trees.forPrint
import com.milaboratory.mixcr.util.XSV
import com.milaboratory.util.ReportHelper
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import java.util.*
import kotlin.math.abs
import kotlin.math.log2

@JsonAutoDetect(
    fieldVisibility = ANY,
    isGetterVisibility = NONE,
    getterVisibility = NONE
)
class BuildSHMTreeReport(
    override val date: Date?,
    override val commandLine: String,
    override val inputFiles: Array<String>,
    override val outputFiles: Array<String>,
    override val executionTimeMillis: Long?,
    override val version: String,
    val stepResults: List<StepResult>
) : MiXCRCommandReport {
    override fun command(): String = CommandFindShmTrees.COMMAND_NAME

    override fun toString(): String = asString()

    override fun writeReport(helper: ReportHelper) {
        // Writing common analysis information
        writeSuperReport(helper)

        for (i in stepResults.indices) {
            val stepResult = stepResults[i]
            helper.writeField("step ${i + 1}", stepResult.step.forPrint)
            if (stepResult.step !is CombineTrees) {
                helper.writeField("Clones was added", stepResult.clonesWasAdded)
            }
            if (stepResult.step is BuildingInitialTrees) {
                helper.writeField("Trees created", stepResult.treesCountDelta)
            } else if (stepResult.step is CombineTrees) {
                helper.writeField("Trees combined", -stepResult.treesCountDelta)
            }
        }
        helper.writeField("Total trees count", stepResults.sumOf { it.treesCountDelta })
        helper.writeField("Total clones count in trees", stepResults.sumOf { it.clonesWasAdded })
    }

    @JsonAutoDetect(
        fieldVisibility = ANY,
        isGetterVisibility = NONE,
        getterVisibility = NONE
    )
    class StepResult(
        val step: BuildSHMTreeStep,
        val clonesWasAdded: Int,
        val cloneNodesWasAdded: Int,
        val treesCountDelta: Int,
        val commonVJMutationsCounts: Stats,
        val clonesCountInTrees: Stats,
        val wildcardsScore: Stats,
        val wildcardsScoreForRoots: Stats,
        val maxNDNsWildcardsScoreInTree: Stats,
        val surenessOfDecisions: Stats,
        val mutationsRateDifferences: Stats,
        val minMutationsRateDifferences: Stats,
        val maxMutationsRateDifferences: Stats,
    )

    @JsonAutoDetect(
        fieldVisibility = ANY,
        isGetterVisibility = NONE,
        getterVisibility = NONE
    )
    data class Stats(
        val size: Long,
        val mean: Double,
        val standardDeviation: Double,
        val sum: Double,
        val min: Double,
        val max: Double,
        val percentile25: Double,
        val percentile50: Double,
        val percentile75: Double
    ) {
        companion object {
            fun from(data: Collection<Double>): Stats {
                val statistics = DescriptiveStatistics()
                data.forEach { statistics.addValue(it) }
                return Stats(
                    size = statistics.n,
                    mean = statistics.mean,
                    standardDeviation = statistics.standardDeviation,
                    sum = statistics.sum,
                    min = statistics.min,
                    max = statistics.max,
                    percentile25 = statistics.getPercentile(25.0),
                    percentile50 = statistics.getPercentile(50.0),
                    percentile75 = statistics.getPercentile(75.0)
                )
            }
        }
    }

    class Builder : AbstractCommandReportBuilder<Builder>() {
        private val stepResults = mutableListOf<StepResult>()
        override fun buildReport(): BuildSHMTreeReport = BuildSHMTreeReport(
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
                .filter { it["cloneId"] != null }
                .groupingBy { it.treeId() }.eachCount()
                .values
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
                .filter { it["cloneId"] != null }
                .filter { it["decisionMetric"] != null }
                .groupBy({ it["cloneId"] }) { it["decisionMetric"]!!.toDouble() }
                .filterValues { it.size > 1 }
                .mapValues { (_, metrics) ->
                    val minMetric = metrics.minOrNull()!!
                    val maxMetric = metrics.minOrNull()!!
                    (maxMetric - minMetric) / maxMetric
                }
                .values
            val mutationRatesDifferences = debugInfosAfterDecisions
                .filter { it["parentId"] != null }
                .filter { it["NDN"] != null }
                .groupBy({ it.treeId() }) { it.mutationsRateDifference() }
                .values

            return StepResult(
                step = step,
                clonesWasAdded = clonesWasAdded,
                cloneNodesWasAdded = cloneNodesWasAdded,
                treesCountDelta = treesCountDelta,
                commonVJMutationsCounts = Stats.from(commonVJMutationsCounts.map { it.toDouble() }),
                clonesCountInTrees = Stats.from(clonesCountInTrees.map { it.toDouble() }),
                wildcardsScore = Stats.from(averageNDNWildcardsScore),
                wildcardsScoreForRoots = Stats.from(NDNsWildcardsScoreForRoots),
                maxNDNsWildcardsScoreInTree = Stats.from(maxNDNsWildcardsScoreInTree),
                surenessOfDecisions = Stats.from(surenessOfDecisions),
                mutationsRateDifferences = Stats.from(mutationRatesDifferences.flatten()),
                minMutationsRateDifferences = Stats.from(mutationRatesDifferences.map { it.minOrNull()!! }),
                maxMutationsRateDifferences = Stats.from(mutationRatesDifferences.map { it.maxOrNull()!! })
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

private fun Map<String, String?>.mutationsRateDifference(): Double {
    val VMutations = getMutations("VMutationsFromParent")
    val VLength = (this["VRangeWithoutCDR3"]!!.split(",") + this["VRangeInCDR3"]!!)
        .map { DebugInfo.decodeRange(it) }
        .sumOf { it.length() }
    val JMutations = getMutations("JMutationsFromParent")
    val JLength = (this["JRangeWithoutCDR3"]!!.split(",") + this["JRangeInCDR3"]!!)
        .map { DebugInfo.decodeRange(it) }
        .sumOf { it.length() }
    val NDNMutations = getMutations("NDNMutationsFromParent")
    val NDNLength = getNucleotideSequence("NDN").size()
    val VJMutationsRate = (VMutations.size() + JMutations.size()) / (VLength + JLength).toDouble()
    val NDNMutationsRate = NDNMutations.size() / NDNLength.toDouble()
    return abs(VJMutationsRate - NDNMutationsRate)
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
