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

import com.fasterxml.jackson.annotation.JsonProperty
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.svg.converter.SvgConverter
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.miplots.toSvg
import com.milaboratory.miplots.writePDF
import com.milaboratory.mixcr.trees.BuildSHMTreeStep
import com.milaboratory.mixcr.trees.BuildSHMTreeStep.BuildingInitialTrees
import com.milaboratory.mixcr.trees.BuildSHMTreeStep.CombineTrees
import com.milaboratory.mixcr.trees.DebugInfo
import com.milaboratory.mixcr.trees.forPrint
import com.milaboratory.mixcr.util.XSV
import com.milaboratory.util.ReportHelper
import jetbrains.letsPlot.geom.geomBar
import jetbrains.letsPlot.geom.geomHistogram
import jetbrains.letsPlot.geom.geomVLine
import jetbrains.letsPlot.ggsize
import jetbrains.letsPlot.intern.Plot
import jetbrains.letsPlot.letsPlot
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Path
import kotlin.math.abs

class BuildSHMTreeReport : MiXCRReport {
    @get:JsonProperty("stepResults")
    val stepResults = mutableListOf<StepResult>()

    fun onStepEnd(step: BuildSHMTreeStep, clonesWasAdded: Int, treesCountDelta: Int) {
        stepResults += StepResult(
            step,
            clonesWasAdded,
            treesCountDelta
        )
    }

    fun addStatsForStep(stepIndex: Int, treesBeforeDecisions: File, treesAfterDecisions: File) {
        val debugInfosBefore = XSV.readXSV(treesBeforeDecisions, DebugInfo.COLUMNS_FOR_XSV.keys, ";")
        val debugInfosAfter = XSV.readXSV(treesAfterDecisions, DebugInfo.COLUMNS_FOR_XSV.keys, ";")
        stepResults[stepIndex].stats = calculateStatsFromDebug(
            debugInfosBefore,
            debugInfosAfter
        )
    }

    private fun calculateStatsFromDebug(
        debugInfosBefore: List<Map<String, String?>>, debugInfosAfter: List<Map<String, String?>>
    ): StepResult.Stats {
        val commonVJMutationsCounts = debugInfosAfter
            .filter { it["parentId"] == "0" }
            .map {
                it.getMutations("VMutationsFromRoot").size() + it.getMutations("JMutationsFromRoot").size()
            }
        val clonesCountInTrees = debugInfosAfter
            .filter { it["cloneId"] != null }
            .groupingBy { it.treeId() }.eachCount()
            .values
        val NDNsByTrees = debugInfosAfter
            .filter { it["id"] != "0" }
            .groupBy { it.treeId() }
            .mapValues { (_, value) -> value.sortedBy { it["id"]!!.toInt() }.map { it.getNucleotideSequence("NDN") } }
        val rootNDNSizes = NDNsByTrees.values.map { it[0].size() }
        val averageNDNWildcardsScore = NDNsByTrees.values
            .flatten()
            .filter { it.size() != 0 }
            .map { it.wildcardsScore() }
            .average()
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
        val surenessOfDecisions = debugInfosBefore
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
        val mutationRatesDifferences = debugInfosAfter
            .filter { it["parentId"] != null }
            .filter { it["NDN"] != null }
            .groupBy({ it.treeId() }) { it.mutationsRateDifference() }

        //TODO diagram and median
        val averageMutationsRateDifference = mutationRatesDifferences.values
            .flatten()
            .average()
        val minMutationsRateDifferences = mutationRatesDifferences.values
            .map { it.minOrNull() ?: 0.0 }
        val maxMutationsRateDifferences = mutationRatesDifferences.values
            .map { it.maxOrNull() ?: 0.0 }
        return StepResult.Stats(
            commonVJMutationsCounts,
            clonesCountInTrees,
            rootNDNSizes,
            averageNDNWildcardsScore,
            NDNsWildcardsScoreForRoots,
            maxNDNsWildcardsScoreInTree,
            surenessOfDecisions,
            averageMutationsRateDifference,
            minMutationsRateDifferences,
            maxMutationsRateDifferences
        )
    }

    fun writePdfReport(output: Path) {
        val commonPageDescriptions = listOf(
            PageDescription(
                "Common VJ mutations counts",
                { commonVJMutationsCounts }
            ),
            PageDescription(
                "Clones count in trees",
                { clonesCountInTrees }
            ),
            PageDescription(
                "Root NDN sizes",
                { rootNDNSizes }
            ),
            PageDescription(
                "NDNs wildcards score for roots",
                { NDNsWildcardsScoreForRoots }
            ) { plot ->
                plot + ggsize(500, 250) + geomHistogram() +
                        geomVLine(xintercept = averageNDNWildcardsScore, color = "green")
            },
            PageDescription(
                "Max NDNs wildcards score in a tree",
                { maxNDNsWildcardsScoreInTree }
            ) { plot ->
                plot + ggsize(500, 250) + geomHistogram() +
                        geomVLine(xintercept = averageNDNWildcardsScore, color = "green")
            }
        )
        val pageDescriptionsForNotPublic = commonPageDescriptions + PageDescription(
            "Min mutations rate differences",
            { minMutationsRateDifferences }
        ) { plot ->
            plot + ggsize(500, 250) + geomHistogram() +
                    geomVLine(xintercept = averageMutationsRateDifference, color = "green")
        } +
                PageDescription(
                    "Max mutations rate differences",
                    { maxMutationsRateDifferences }
                ) { plot ->
                    plot + ggsize(500, 250) + geomHistogram() +
                            geomVLine(xintercept = averageMutationsRateDifference, color = "green")
                }
        writePDF(output, pageDescriptionsForNotPublic.printPages())
    }

    override fun writeReport(helper: ReportHelper) {
        for (i in stepResults.indices) {
            val stepResult = stepResults[i]
            helper.writeField("step ${i + 1}", stepResult.step.forPrint)
            if (stepResult.step is CombineTrees) {
                helper.writeField("Clones was added", stepResult.clonesWasAdded)
            }
            if (stepResult.step is BuildingInitialTrees) {
                helper.writeField("Trees created", stepResult.treesCountDelta)
            } else if (stepResult.step !is CombineTrees) {
                helper.writeField("Trees combined", -stepResult.treesCountDelta)
            }
        }
        helper.writeField("Total trees count", stepResults.sumOf { it.treesCountDelta })
        helper.writeField("Total clones count in trees", stepResults.sumOf { it.clonesWasAdded })
    }

    private fun List<PageDescription>.printPages(): List<ByteArray> =
        map { (title, field, plotBuilder) ->
            val bs = ByteArrayOutputStream()
            val pdfDoc = PdfDocument(PdfWriter(bs))

            Document(pdfDoc).use { document ->
                document.add(Paragraph(title))

                for (i in stepResults.indices) {
                    val stepResult = stepResults[i]
                    val stepDescription = "Step ${i + 1}: ${stepResult.step.forPrint}"
                    val content = field(stepResult.stats)
                    if (content.isEmpty()) {
                        continue
                    }
                    val data = mapOf(
                        stepDescription to content
                    )
                    val plot = letsPlot(data) {
                        x = stepDescription
                    }
                    val svg = plotBuilder(stepResult.stats, plot).toSvg()
                    document.add(SvgConverter.convertToImage(ByteArrayInputStream(svg.toByteArray()), pdfDoc))
                }
            }
            bs.toByteArray()
        }

    data class PageDescription(
        val title: String,
        val field: StepResult.Stats.() -> Collection<Any>,
        val plotBuilder: StepResult.Stats.(Plot) -> Plot = { plot -> plot + ggsize(500, 250) + geomBar() }
    )

    class StepResult(
        @get:JsonProperty("step") val step: BuildSHMTreeStep,
        @get:JsonProperty("clonesWasAdded") val clonesWasAdded: Int,
        @get:JsonProperty("treesCountDelta") val treesCountDelta: Int
    ) {
        @get:JsonProperty("stats")
        lateinit var stats: Stats

        class Stats(
            @get:JsonProperty("commonVJMutationsCounts") val commonVJMutationsCounts: List<Int>,
            @get:JsonProperty("clonesCountInTrees") val clonesCountInTrees: Collection<Int>,
            @get:JsonProperty("rootNDNSizes") val rootNDNSizes: List<Int>,
            @get:JsonProperty("averageNDNWildcardsScore") val averageNDNWildcardsScore: Double,
            @get:JsonProperty("NDNsWildcardsScoreForRoots") val NDNsWildcardsScoreForRoots: List<Double>,
            @get:JsonProperty("maxNDNsWildcardsScoreInTree") val maxNDNsWildcardsScoreInTree: List<Double>,
            @get:JsonProperty("surenessOfDecisions") val surenessOfDecisions: Collection<Double>,
            @get:JsonProperty("averageMutationsRateDifference") val averageMutationsRateDifference: Double,
            @get:JsonProperty("minMutationsRateDifferences") val minMutationsRateDifferences: List<Double>,
            @get:JsonProperty("maxMutationsRateDifferences") val maxMutationsRateDifferences: List<Double>
        )
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
    .average()
