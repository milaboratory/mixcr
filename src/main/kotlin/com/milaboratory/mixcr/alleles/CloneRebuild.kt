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

import cc.redberry.pipe.OutputPort
import com.milaboratory.core.Range
import com.milaboratory.core.alignment.Aligner
import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.alignment.AlignmentUtils
import com.milaboratory.core.mutations.Mutation
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.alleles.AlleleUtil.complimentaryGeneType
import com.milaboratory.mixcr.assembler.CloneFactory
import com.milaboratory.mixcr.assembler.CloneFactoryParameters
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.GeneAndScore
import com.milaboratory.mixcr.basictypes.GeneFeatures
import com.milaboratory.mixcr.basictypes.VDJCHit
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.cli.logger
import com.milaboratory.mixcr.trees.MutationsUtils
import com.milaboratory.mixcr.util.asSequence
import com.milaboratory.primitivio.asSequence
import com.milaboratory.primitivio.mapInParallel
import com.milaboratory.primitivio.toList
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Constant
import io.repseq.core.GeneType.Diversity
import io.repseq.core.GeneType.VJ_REFERENCE
import io.repseq.core.VDJCGeneId
import io.repseq.core.VDJCLibrary
import io.repseq.dto.VDJCGeneData
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.LongAdder
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.set

class CloneRebuild(
    private val resultLibrary: VDJCLibrary,
    private val allelesMapping: Map<String, List<VDJCGeneId>>,
    assemblingFeatures: GeneFeatures,
    private val threads: Int,
    cloneFactoryParameters: CloneFactoryParameters,
    featuresToAlign: EnumMap<GeneType, GeneFeature>
) {
    private val cloneFactory = CloneFactory(
        cloneFactoryParameters,
        assemblingFeatures.features.toTypedArray(),
        resultLibrary.genes,
        featuresToAlign
    )

    /**
     * Realign clones with new scores
     */
    fun rebuildClones(withRecalculatedScores: OutputPort<Pair<Clone, EnumMap<GeneType, List<GeneAndScore>>>>): List<Clone> =
        withRecalculatedScores
            .mapInParallel(threads) { (clone, recalculateScores) ->
                cloneFactory.create(
                    clone.id,
                    clone.count,
                    recalculateScores,
                    clone.tagCount,
                    clone.targets,
                    clone.group
                )
            }
            .asSequence()
            .sortedBy { it.id }
            .toList()

    /**
     * For every clone will be added hits aligned to found alleles.
     * If there is no zero allele, initial hit will be deleted from a clone.
     * Only top hits will be saved (VJCClonalAlignerParameters#relativeMinScore param)
     */
    fun recalculateScores(
        input: OutputPort<Clone>,
        tagsInfo: TagsInfo,
        reportBuilder: FindAllelesReport.Builder
    ): List<Pair<Clone, EnumMap<GeneType, List<GeneAndScore>>>> {
        val errorsCount: MutableMap<VDJCGeneId, LongAdder> = ConcurrentHashMap()
        val withRecalculatedScores = input
            .mapInParallel(threads) { clone ->
                val recalculateScores = EnumMap<GeneType, List<GeneAndScore>>(GeneType::class.java)
                //copy D and C
                for (gt in arrayOf(Diversity, Constant)) {
                    recalculateScores[gt] = clone.getHits(gt).map { hit ->
                        val mappedGeneId = VDJCGeneId(resultLibrary.libraryId, hit.gene.name)
                        GeneAndScore(mappedGeneId, hit.score)
                    }
                }
                for (geneType in VJ_REFERENCE) {
                    val changes: MutableMap<VDJCGeneId, AlignmentsChange> = mutableMapOf()

                    for (hit in clone.getHits(geneType)) {
                        for (foundAlleleId in allelesMapping[hit.gene.name]!!) {
                            changes[foundAlleleId] = alignmentsChange(
                                clone,
                                resultLibrary[foundAlleleId.name].data,
                                cloneFactory.parameters.getVJCParameters(geneType).scoring,
                                hit
                            )
                        }
                    }
                    val maxScore = changes.values.maxOf { it.newScore }
                    val scoreThreshold = when {
                        maxScore <= 0 -> {
                            //in case of clone that is too different with found allele
                            errorsCount.computeIfAbsent(clone.getBestHit(geneType).gene.id) { LongAdder() }.increment()
                            changes.values.minOf { it.newScore }
                        }
                        else -> maxScore * cloneFactory.parameters.getVJCParameters(geneType).relativeMinScore
                    }
                    recalculateScores[geneType] = changes
                        .filter { it.value.newScore >= scoreThreshold }
                        .map { (alleleGeneId, alignmentsChange) ->
                            GeneAndScore(alleleGeneId, alignmentsChange.newScore)
                        }

                    val bestAlleleId = changes.entries.maxByOrNull { it.value.newScore }!!.key
                    reportBuilder.overallAllelesStatistics.registerBaseGene(changes[bestAlleleId]!!.originalHit.gene.id)

                    for ((alleleId, alignmentsChange) in changes) {
                        val stats = reportBuilder.overallAllelesStatistics.stats(alleleId)
                        if (alleleId == bestAlleleId) {
                            val complementaryGeneId = clone.getBestHit(complimentaryGeneType(geneType)).gene.id
                            stats.register(clone, complementaryGeneId, tagsInfo)
                            if (alignmentsChange.naive) {
                                stats.naive(clone, tagsInfo)
                            }
                            stats.scoreDelta(clone, alignmentsChange.scoreDelta, tagsInfo)
                        }
                    }
                }
                var scoreDelta = 0.0F
                for (geneType in VJ_REFERENCE) {
                    scoreDelta += recalculateScores[geneType]!!.maxOf { it.score } - clone.getBestHit(geneType).score
                }
                reportBuilder.scoreDelta(scoreDelta)
                clone to recalculateScores
            }
            .toList()
        errorsCount.forEach { (geneId, count) ->
            logger.warn("for $geneId found $count clones that get negative score after realigning")
        }
        return withRecalculatedScores
    }

    data class AlignmentsChange(
        val originalHit: VDJCHit,
        val naive: Boolean,
        val scoreDelta: Float,
        val penalty: Double
    ) {
        val newScore: Float get() = originalHit.score + scoreDelta
    }

    companion object {
        fun alignmentsChange(
            clone: Clone,
            foundAllele: VDJCGeneData,
            scoring: AlignmentScoring<NucleotideSequence>,
            hit: VDJCHit = clone.getBestHit(foundAllele.geneType)
        ): AlignmentsChange = when {
            foundAllele.name != hit.gene.name -> calculateChange(clone, foundAllele, scoring, hit)
            else -> AlignmentsChange(
                hit,
                hit.alignments.asSequence().sumOf { it.absoluteMutations.size() } == 0,
                0.0F,
                hit.alignments.asSequence()
                    .sumOf { it.sequence1Range.length() * scoring.maximalMatchScore - it.score.toDouble() }
            )
        }

        /**
         * Recalculate score and mutations for every alignment based on found allele
         */
        private fun calculateChange(
            clone: Clone,
            foundAllele: VDJCGeneData,
            scoring: AlignmentScoring<NucleotideSequence>,
            hit: VDJCHit
        ): AlignmentsChange {
            val alleleMutations = foundAllele.baseSequence.mutations ?: throw IllegalArgumentException()
            val alleleHasIndels = alleleMutations.asSequence().any { Mutation.isInDel(it) }
            var scoreDelta = 0.0f
            var noMutations = true
            var penalty = 0.0
            hit.alignments
                .filterNotNull()
                .forEachIndexed { index, alignment ->
                    val maxScore = alignment.sequence1Range.length() * scoring.maximalMatchScore
                    val (recalculatedMutationsCount, recalculatedScore) = when {
                        //in case of indels invert().combineWith(alleleMutations).invert() may work incorrect
                        alleleHasIndels -> {
                            val seq1RangeAfterAlleleMutations = Range(
                                MutationsUtils.positionIfNucleotideWasDeleted(
                                    alleleMutations.convertToSeq2Position(
                                        alignment.sequence1Range.lower
                                    )
                                ),
                                MutationsUtils.positionIfNucleotideWasDeleted(
                                    alleleMutations.convertToSeq2Position(
                                        alignment.sequence1Range.upper
                                    )
                                )
                            )
                            val newAlignment = Aligner.alignGlobal(
                                scoring,
                                alleleMutations.mutate(alignment.sequence1),
                                clone.getTarget(index).sequence,
                                seq1RangeAfterAlleleMutations.lower,
                                seq1RangeAfterAlleleMutations.length(),
                                alignment.sequence2Range.lower,
                                alignment.sequence2Range.length(),
                            )
                            newAlignment.absoluteMutations.size() to newAlignment.score
                        }
                        else -> {
                            val mutationsFromAllele =
                                alignment.absoluteMutations.invert().combineWith(alleleMutations).invert()
                                    .extractAbsoluteMutationsForRange(alignment.sequence1Range)
                            mutationsFromAllele.size() to AlignmentUtils.calculateScore(
                                alleleMutations.mutate(alignment.sequence1),
                                alignment.sequence1Range,
                                mutationsFromAllele,
                                scoring
                            ).toFloat()
                        }
                    }
                    noMutations = noMutations && recalculatedMutationsCount == 0
                    scoreDelta += recalculatedScore - alignment.score
                    penalty += maxScore - recalculatedScore
                }
            return AlignmentsChange(
                hit,
                noMutations,
                scoreDelta,
                penalty
            )
        }

    }
}
