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
package com.milaboratory.mixcr.alleles

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.NONE
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.alleles.CloneDescription.MutationGroup
import com.milaboratory.mixcr.cli.AbstractCommandReportBuilder
import com.milaboratory.mixcr.cli.AbstractMiXCRCommandReport
import com.milaboratory.mixcr.cli.CommandFindAlleles
import com.milaboratory.mixcr.cli.MiXCRCommandReport
import com.milaboratory.util.ReportHelper
import org.apache.commons.math3.stat.descriptive.SummaryStatistics
import org.apache.commons.math3.stat.descriptive.SynchronizedSummaryStatistics
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.LongAdder

@JsonAutoDetect(
    fieldVisibility = ANY,
    isGetterVisibility = NONE,
    getterVisibility = NONE
)
class FindAllelesReport(
    date: Date?,
    commandLine: String,
    inputFiles: Array<String>,
    outputFiles: Array<String>,
    executionTimeMillis: Long?,
    version: String,
    private val searchHistoryForBCells: SortedMap<String, SearchHistoryForBCells>,
    private val clonesCountWithNoChangeOfScore: Long,
    private val clonesCountWithNegativeScoreChange: Long,
    private val clonesScoreDeltaStats: MiXCRCommandReport.StandardStats,
    private val foundAlleles: Int,
    private val zygotes: SortedMap<Int, Int>,
    private val allelesScoreChange: SortedMap<String, MiXCRCommandReport.StandardStats>
) : AbstractMiXCRCommandReport(date, commandLine, inputFiles, outputFiles, executionTimeMillis, version) {
    override fun command(): String = CommandFindAlleles.COMMAND_NAME

    override fun writeReport(helper: ReportHelper) {
        // Writing common analysis information
        writeSuperReport(helper)

        helper.write("Clones score delta stats", clonesScoreDeltaStats)
        helper.writeField("Clones count with no change of score", clonesCountWithNoChangeOfScore)
        helper.writeField("Clones count with negative score change", clonesCountWithNegativeScoreChange)
        helper.writeField("Found alleles", foundAlleles)
        helper.writeField("Zygotes", zygotes)
        helper.writeField("Not enough information for allele search",
            searchHistoryForBCells
                .filterValues { (alleles) ->
                    alleles.result.isEmpty()
                }
                .keys
        )
        helper.writeField("The same mutations in almost all clones, but not enough naive clones",
            searchHistoryForBCells
                .filterValues { (alleles) ->
                    alleles.result.isEmpty() && alleles.filteredOutByNaiveCount.size == 1
                }
                .filterValues { (alleles) ->
                    val possibleMutation = alleles.filteredOutByNaiveCount.first().mutations
                    possibleMutation != "" && alleles.enrichedMutations[possibleMutation] == possibleMutation
                }
                //filter genes that have some info
                .filterValues { it.enoughDiversity }
                .mapValues { (_, history) ->
                    history.alleles.filteredOutByNaiveCount.first().mutations
                }
        )
        helper.writeField("Possible additional allele, but not enough naive clones",
            searchHistoryForBCells
                .filterValues { (alleles) ->
                    alleles.result.isNotEmpty()
                }
                .mapValues { (_, history) ->
                    history.alleles.filteredOutByNaiveCount.map { it.mutations }
                }
                .filterValues { it.isNotEmpty() }
        )
        helper.writeField("Found alleles that don't fit well to data",
            allelesScoreChange
                .filterValues { it.avg < 0 || it.quadraticMean < 0 }
                .mapValues { "{ avgScoreDelta: ${it.value.avg}, quadraticMeanOfScoreDelta: ${it.value.quadraticMean}" }
        )
    }

    @JsonAutoDetect(
        fieldVisibility = ANY,
        isGetterVisibility = NONE,
        getterVisibility = NONE
    )
    data class AlleleCandidate(
        val mutations: String,
        val count: Int,
        val countOfSureAlign: Int,
        val naiveCount: Int
    )

    data class SearchHistoryForBCells(
        val alleles: Alleles,
        val clonesCount: Int,
        val diversity: Int,
        val enoughDiversity: Boolean,
        val differentMutationsCount: Int,
        val mutationsWithEnoughDiversityCount: Int
    ) {
        data class Alleles(
            val addedKnownAllele: String?,
            val enrichedMutations: Map<String, String>,
            val discardedEnrichedMutations: List<String>,
            val filteredOutByNaiveCount: List<AlleleCandidate>,
            val result: List<String>
        )
    }


    class Builder(
        val overallAllelesStatistics: OverallAllelesStatistics
    ) : AbstractCommandReportBuilder<Builder>() {
        private val foundAlleles = AtomicInteger(0)
        private val zygotes: MutableMap<Int, LongAdder> = ConcurrentHashMap()
        private val clonesCountWithNoChangeOfScore: LongAdder = LongAdder()
        private val clonesCountWithNegativeScoreChange: LongAdder = LongAdder()
        private val clonesScoreDeltaStats: SummaryStatistics = SynchronizedSummaryStatistics()
        private val searchHistoryForBCells: MutableMap<String, SearchHistoryForBCellsBuilder> = ConcurrentHashMap()

        fun historyForBCells(
            geneName: String,
            clonesCount: Int,
            diversity: Int,
            minDiversityForAllele: Int
        ): SearchHistoryForBCellsBuilder {
            val result = SearchHistoryForBCellsBuilder(clonesCount, diversity, diversity >= minDiversityForAllele)
            require(searchHistoryForBCells.put(geneName, result) == null)
            return result
        }

        fun scoreDelta(delta: Float) {
            if (delta == 0.0F) {
                clonesCountWithNoChangeOfScore.increment()
            } else {
                if (delta < 0.0F) {
                    clonesCountWithNegativeScoreChange.increment()
                }
                clonesScoreDeltaStats.addValue(delta.toDouble())
            }
        }

        fun foundAlleles(count: Int) {
            foundAlleles.addAndGet(count)
        }

        fun zygote(count: Int) {
            zygotes.computeIfAbsent(count) { LongAdder() }.increment()
        }

        override fun buildReport() = FindAllelesReport(
            date,
            commandLine,
            inputFiles,
            outputFiles,
            executionTimeMillis,
            version,
            searchHistoryForBCells.mapValuesTo(TreeMap()) { (_, history) ->
                SearchHistoryForBCells(
                    alleles = SearchHistoryForBCells.Alleles(
                        addedKnownAllele = history.alleles.addedKnownAllele?.asMutations()?.encode(","),
                        discardedEnrichedMutations = history.alleles.discardedEnrichedMutations
                            .map { it.asMutations().encode(",") },
                        enrichedMutations = history.alleles.enrichedMutations
                            .mapKeys { (it, _) -> it.asMutations().encode(",") }
                            .mapValues { (_, it) -> it.encode(",") },
                        filteredOutByNaiveCount = history.alleles.filteredOutByNaiveCount,
                        result = history.alleles.result
                            .map { it.asMutations().encode(",") }
                    ),
                    clonesCount = history.clonesCount,
                    diversity = history.diversity,
                    enoughDiversity = history.enoughDiversity,
                    differentMutationsCount = history.differentMutationsCount!!,
                    mutationsWithEnoughDiversityCount = history.mutationsWithEnoughDiversityCount!!
                )
            },
            clonesCountWithNoChangeOfScore.sum(),
            clonesCountWithNegativeScoreChange.sum(),
            MiXCRCommandReport.StandardStats.from(clonesScoreDeltaStats),
            foundAlleles.get(),
            zygotes.mapValuesTo(TreeMap()) { it.value.toInt() },
            overallAllelesStatistics.stats
                .mapKeys { it.key.name }
                .mapValues { MiXCRCommandReport.StandardStats.from(it.value.scoreDelta) }
                .filterValues { it.size != 0L }
                .toSortedMap()
        )

        override fun that() = this

        class SearchHistoryForBCellsBuilder(
            val clonesCount: Int,
            val diversity: Int,
            val enoughDiversity: Boolean
        ) {
            var differentMutationsCount: Int? = null
            var mutationsWithEnoughDiversityCount: Int? = null

            val alleles = Alleles()

            class Alleles {
                var addedKnownAllele: List<MutationGroup>? = null
                var enrichedMutations: Map<List<MutationGroup>, Mutations<NucleotideSequence>> = emptyMap()
                var discardedEnrichedMutations: MutableList<List<MutationGroup>> = mutableListOf()
                lateinit var filteredOutByNaiveCount: List<AlleleCandidate>
                lateinit var result: List<List<MutationGroup>>
            }
        }
    }
}
