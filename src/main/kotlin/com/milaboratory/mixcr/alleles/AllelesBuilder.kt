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

import cc.redberry.pipe.OutputPortCloseable
import com.milaboratory.core.Range
import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.CloneReader
import com.milaboratory.mixcr.util.ClonesAlignmentRanges
import com.milaboratory.mixcr.util.asMutations
import com.milaboratory.mixcr.util.asSequence
import com.milaboratory.primitivio.filter
import com.milaboratory.primitivio.flatten
import io.repseq.core.*
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.dto.VDJCGeneData
import java.util.*
import kotlin.collections.set

class AllelesBuilder(
    private val parameters: FindAllelesParameters,
    val datasets: List<CloneReader>
) {
    private val VScoring = datasets[0].assemblerParameters.cloneFactoryParameters.vParameters.scoring
    private val JScoring = datasets[0].assemblerParameters.cloneFactoryParameters.jParameters.scoring

    fun count() = datasets.sumOf { it.numberOfClones() }.toLong()

    fun unsortedClones() = datasets.map { it.readClones() }
        .flatten()
        .filter { c ->
            // filter non-productive clonotypes
            // todo CDR3?
            !parameters.productiveOnly || (!c.containsStops(CDR3) && !c.isOutOfFrame(CDR3))
        }
        .filter { c ->
            c.count > parameters.useClonesWithCountMoreThen
        }

    private fun findAlleles(clusterByTheSameGene: List<Clone>, geneType: GeneType): List<Allele> {
        require(clusterByTheSameGene.isNotEmpty())
        val commonAlignmentRanges = ClonesAlignmentRanges.commonAlignmentRanges(
            clusterByTheSameGene,
            parameters.minPortionOfClonesForCommonAlignmentRanges,
            geneType
        ) { it.getBestHit(geneType) }
        val bestHit = clusterByTheSameGene[0].getBestHit(geneType)
        val complimentaryGene = complimentaryGene(geneType)
        val cloneDescriptors = clusterByTheSameGene.asSequence()
            .filter { commonAlignmentRanges.containsClone(it) }
            .map { clone ->
                CloneDescription(
                    clone.getBestHit(geneType).alignments.asSequence()
                        .flatMap { it.absoluteMutations.asSequence() }
                        .filter { commonAlignmentRanges.containsMutation(it) }
                        .asMutations(NucleotideSequence.ALPHABET),
                    clone.ntLengthOf(CDR3),
                    clone.getBestHit(complimentaryGene).gene.geneName
                )
            }
            .toList()
        val allelesSearcher: AllelesSearcher = TIgGERAllelesSearcher(
            scoring(geneType),
            clusterByTheSameGene.first().getBestHit(geneType).alignments[0].sequence1,
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
                    commonAlignmentRanges.commonRanges
                )
            }
    }

    private fun complimentaryGene(geneType: GeneType): GeneType = when (geneType) {
        Variable -> Joining
        Joining -> Variable
        else -> throw IllegalArgumentException()
    }

    private fun scoring(geneType: GeneType): AlignmentScoring<NucleotideSequence> = when (geneType) {
        Variable -> VScoring
        Joining -> JScoring
        else -> throw IllegalArgumentException()
    }

    fun allelesGeneData(cluster: List<Clone>, geneType: GeneType): List<VDJCGeneData> =
        findAlleles(cluster, geneType)
            .map { allele ->
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

    class SortedClonotypes(
        private val sortedByV: OutputPortCloseable<Clone>,
        private val sortedByJ: OutputPortCloseable<Clone>
    ) {
        fun getSortedBy(geneType: GeneType): OutputPortCloseable<Clone> = when (geneType) {
            Variable -> sortedByV
            Joining -> sortedByJ
            else -> throw IllegalArgumentException()
        }
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
