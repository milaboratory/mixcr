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
@file:Suppress("PrivatePropertyName")

package com.milaboratory.mixcr.alleles

import cc.redberry.pipe.OutputPort
import com.milaboratory.core.Range
import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.mutations.Mutation
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.CloneReader
import com.milaboratory.mixcr.trees.constructStateBuilder
import com.milaboratory.mixcr.util.VJPair
import com.milaboratory.mixcr.util.alignmentsCover
import com.milaboratory.mixcr.util.asMutations
import com.milaboratory.mixcr.util.asSequence
import com.milaboratory.primitivio.GroupingCriteria
import com.milaboratory.primitivio.filter
import com.milaboratory.primitivio.flatten
import com.milaboratory.primitivio.groupBy
import com.milaboratory.primitivio.mapInParallel
import com.milaboratory.primitivio.toList
import com.milaboratory.primitivio.withProgress
import com.milaboratory.util.ProgressAndStage
import com.milaboratory.util.TempFileDest
import io.repseq.core.BaseSequence
import io.repseq.core.GeneFeature
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.ReferencePoint
import io.repseq.core.VDJCGene
import io.repseq.dto.VDJCGeneData
import java.util.*
import kotlin.collections.set

class AllelesBuilder(
    private val parameters: FindAllelesParameters,
    private val tempDest: TempFileDest,
    private val datasets: List<CloneReader>,
    private val geneFeatureToMatch: VJPair<GeneFeature>
) {
    private val scoring: VJPair<AlignmentScoring<NucleotideSequence>> = VJPair(
        V = datasets[0].assemblerParameters.cloneFactoryParameters.vParameters.scoring,
        J = datasets[0].assemblerParameters.cloneFactoryParameters.jParameters.scoring
    )

    fun searchForAlleles(
        geneType: GeneType,
        progress: ProgressAndStage,
        threads: Int
    ): List<Pair<String, List<VDJCGeneData>>> {
        val totalClonesCount = datasets.sumOf { it.numberOfClones() }.toLong()
        val stateBuilder = datasets.constructStateBuilder()

        //assumption: there are no allele genes in library
        //TODO how to check assumption?
        return filteredClones { filteredClones ->
            filteredClones.withProgress(
                totalClonesCount,
                progress,
                "Grouping by the same ${geneType.letter} gene"
            ) { clones ->
                clones.groupBy(
                    stateBuilder,
                    tempDest.addSuffix("alleles.searcher.${geneType.letterLowerCase}"),
                    GroupingCriteria.groupBy { it.getBestHit(geneType).gene }
                )
            }
                .withProgress(
                    totalClonesCount,
                    progress,
                    "Searching for ${geneType.letter} alleles",
                    countPerElement = { it.size.toLong() }
                ) { clustersWithTheSameV ->
                    clustersWithTheSameV.mapInParallel(threads) { cluster ->
                        val geneId = cluster[0].getBestHit(geneType).gene.name
                        geneId to findAlleles(cluster, geneType).toGeneData()
                    }
                        .toList()
                }
        }
    }


    private fun <R> filteredClones(function: (OutputPort<Clone>) -> R): R = datasets.map { it.readClones() }
        .flatten()
        .filter { c ->
            // filter non-productive clonotypes
            // todo CDR3?
            !parameters.productiveOnly || (!c.containsStops(CDR3) && !c.isOutOfFrame(CDR3))
        }
        .filter { c ->
            c.count > parameters.useClonesWithCountMoreThen
        }
        .use(function)

    private fun findAlleles(clusterByTheSameGene: List<Clone>, geneType: GeneType): List<Allele> {
        require(clusterByTheSameGene.isNotEmpty())
        val bestHit = clusterByTheSameGene[0].getBestHit(geneType)
        //TODO remove after moving cut logic to contig (left filter of CDR3 mutations)
        val partitioning = bestHit.gene.partitioning.getRelativeReferencePoints(bestHit.alignedFeature)
        val rangesToMatch = partitioning.getRanges(geneFeatureToMatch[geneType])

        val complimentaryGeneType = complimentaryGeneType(geneType)
        val cloneDescriptors = clusterByTheSameGene.asSequence()
            //TODO remove after moving cut logic to contig
            .filter { clone ->
                clone.getBestHit(geneType).alignmentsCover(rangesToMatch)
            }
            .map { clone ->
                CloneDescription(
                    clone.getBestHit(geneType).alignments.asSequence()
                        .filterNotNull()
                        .flatMap { it.absoluteMutations.asSequence() }
                        .filter { mutation ->
                            val position = Mutation.getPosition(mutation)
                            rangesToMatch.any { toMatch -> position in toMatch }
                        }
                        .asMutations(NucleotideSequence.ALPHABET),
                    clone.ntLengthOf(CDR3),
                    clone.getBestHit(complimentaryGeneType).gene.geneName
                )
            }
            .toList()
        val allelesSearcher: AllelesSearcher = TIgGERAllelesSearcher(
            scoring[geneType],
            clusterByTheSameGene.first().getBestHit(geneType).alignments.filterNotNull().first().sequence1,
            parameters
        )

        //TODO search for mutations in CDR3
        // iterate over positions in CDR3 and align every clone to germline
        // get mutations of every clone as proposals.
        // Align every clone against every proposal. Choose proposal with maximum sum of score.
        // Calculate sum of score fine on a letter in a sliding window.
        // If it decreasing more than constant in left and right parts of a window, than stop (decide what choose as an end).
        // May be size of a window depends on clones count
        //
        // What to do with P segment? May be use previous decisions as germline or generate more proposals based on mirroring
        //
        // Why it will works: on the end of a gene we will get chaotic nucleotides, otherwise few clones will have
        // mutation that will not correspond with others in this position.
        // So if it is an allele mutation score will decrease slightly and dramatically otherwise.
        // Sliding window will allow to make decisions even on small count of clones (voting will be on 'count of clones' * 'window size')
        return allelesSearcher.search(cloneDescriptors)
            .map {
                Allele(
                    bestHit.gene,
                    it.allele,
                    bestHit.alignedFeature,
                    rangesToMatch
                )
            }
    }

    private fun complimentaryGeneType(geneType: GeneType): GeneType = when (geneType) {
        Variable -> Joining
        Joining -> Variable
        else -> throw IllegalArgumentException()
    }

    private fun List<Allele>.toGeneData() =
        map { allele ->
            when {
                allele.mutations != Mutations.EMPTY_NUCLEOTIDE_MUTATIONS -> buildGene(allele)
                else -> allele.gene.data
            }
        }
            .sortedBy { it.name }

    private fun buildGene(allele: Allele): VDJCGeneData = VDJCGeneData(
        BaseSequence(
            allele.gene.data.baseSequence.origin,
            allele.gene.partitioning.getRanges(allele.alignedFeature),
            allele.mutations
        ),
        generateGeneName(allele),
        allele.gene.data.geneType,
        allele.gene.data.isFunctional,
        allele.gene.data.chains,
        metaForGeneratedGene(allele),
        recalculatedAnchorPoints(allele)
    )

    private fun generateGeneName(allele: Allele): String =
        allele.gene.name + "-M" + allele.mutations.size() + "-" + allele.mutations.hashCode()

    private fun metaForGeneratedGene(allele: Allele): SortedMap<String, SortedSet<String>> {
        val meta: SortedMap<String, SortedSet<String>> = TreeMap(allele.gene.data.meta)
        meta["alleleMutationsReliableRanges"] = allele.knownRanges
            .map { it.toString() }
            .toSortedSet()
        meta["alleleVariantOf"] = sortedSetOf(allele.gene.name)
        return meta
    }

    private fun recalculatedAnchorPoints(allele: Allele): TreeMap<ReferencePoint, Long> {
        val mappedReferencePoints = allele.gene.partitioning
            .getRelativeReferencePoints(allele.alignedFeature)
            .applyMutations(allele.mutations)
        return (0 until mappedReferencePoints.pointsCount()).asSequence()
            .map { index -> mappedReferencePoints.referencePointFromIndex(index) }
            .associateByTo(TreeMap(), { it }, { mappedReferencePoints.getPosition(it).toLong() })
    }

    private class Allele(
        val gene: VDJCGene,
        val mutations: Mutations<NucleotideSequence>,
        val alignedFeature: GeneFeature,
        val knownRanges: Array<Range>
    ) {
        override fun toString(): String = "Allele{" +
                "id=" + gene.name +
                ", mutations=" + mutations +
                '}'
    }
}
