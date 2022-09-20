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
import com.milaboratory.core.alignment.Alignment
import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.alignment.AlignmentUtils
import com.milaboratory.core.mutations.Mutation
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters
import com.milaboratory.mixcr.assembler.CloneFactory
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.GeneAndScore
import com.milaboratory.mixcr.basictypes.GeneFeatures
import com.milaboratory.mixcr.basictypes.VDJCHit
import com.milaboratory.mixcr.trees.MutationsUtils
import com.milaboratory.mixcr.util.asSequence
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import com.milaboratory.primitivio.asSequence
import com.milaboratory.primitivio.mapInParallel
import com.milaboratory.primitivio.toList
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Constant
import io.repseq.core.GeneType.Diversity
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.VJ_REFERENCE
import io.repseq.core.GeneType.Variable
import io.repseq.core.VDJCGene
import io.repseq.core.VDJCGeneId
import io.repseq.core.VDJCLibrary
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
    assemblerParameters: CloneAssemblerParameters,
    alignerParameters: VDJCAlignerParameters
) {
    private val cloneFactory = CloneFactory(
        assemblerParameters.cloneFactoryParameters,
        assemblingFeatures.features,
        resultLibrary.genes,
        alignerParameters.featuresToAlignMap
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
        overallAllelesStatistics: OverallAllelesStatistics,
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
                            changes[foundAlleleId] = when {
                                foundAlleleId.name != hit.gene.name -> {
                                    val (scoreDelta, recalculatedMutationsCount) = scoreDelta(
                                        clone,
                                        resultLibrary[foundAlleleId.name],
                                        cloneFactory.parameters.getVJCParameters(geneType).scoring,
                                        hit.alignments
                                    )
                                    AlignmentsChange(
                                        hit,
                                        scoreDelta,
                                        recalculatedMutationsCount
                                    )
                                }
                                else -> AlignmentsChange(hit)
                            }
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
                    overallAllelesStatistics.registerBaseGene(changes[bestAlleleId]!!.originalHit.gene.id)

                    for ((alleleId, alignmentsChange) in changes) {
                        val stats = overallAllelesStatistics.stats(alleleId)
                        if (alleleId == bestAlleleId) {
                            val complementaryGeneId = clone.getBestHit(complimentaryGeneType(geneType)).gene.id
                            stats.register(clone.ntLengthOf(CDR3), complementaryGeneId)
                            if (alignmentsChange.mutationsCount == 0) {
                                stats.naives.increment()
                            }
                            stats.scoreDelta(alignmentsChange.scoreDelta)
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
            println("WARN: for $geneId found $count clones that get negative score after realigning")
        }
        return withRecalculatedScores
    }

    /**
     * Recalculate score and mutations for every alignment based on found allele
     */
    private fun scoreDelta(
        clone: Clone,
        foundAllele: VDJCGene,
        scoring: AlignmentScoring<NucleotideSequence>,
        alignments: Array<Alignment<NucleotideSequence>?>
    ): Pair<Float, Int> {
        val alleleMutations = foundAllele.data.baseSequence.mutations ?: throw IllegalArgumentException()
        val alleleHasIndels = alleleMutations.asSequence().any { Mutation.isInDel(it) }
        var scoreDelta = 0.0f
        var mutationsCount = 0
        alignments
            .filterNotNull()
            .forEachIndexed { index, alignment ->
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
                mutationsCount += recalculatedMutationsCount
                scoreDelta += recalculatedScore - alignment.score
            }
        return scoreDelta to mutationsCount
    }

    private data class AlignmentsChange(
        val originalHit: VDJCHit,
        val scoreDelta: Float = 0.0F,
        val mutationsCount: Int = 0
    ) {
        val newScore: Float get() = originalHit.score + scoreDelta
    }
}

private fun complimentaryGeneType(geneType: GeneType): GeneType = when (geneType) {
    Variable -> Joining
    Joining -> Variable
    else -> throw IllegalArgumentException()
}
