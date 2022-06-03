@file:Suppress("PrivatePropertyName")

package com.milaboratory.mixcr.alleles

import cc.redberry.pipe.CUtils
import cc.redberry.pipe.OutputPort
import cc.redberry.pipe.OutputPortCloseable
import cc.redberry.pipe.blocks.FilteringPort
import cc.redberry.pipe.util.FlatteningOutputPort
import com.milaboratory.core.Range
import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.CloneReader
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.util.ClonesAlignmentRanges
import com.milaboratory.mixcr.util.Cluster
import com.milaboratory.mixcr.util.ExceptionUtil
import com.milaboratory.mixcr.util.asMutations
import com.milaboratory.mixcr.util.asSequence
import com.milaboratory.primitivio.PrimitivIOStateBuilder
import com.milaboratory.util.sorting.HashSorter
import io.repseq.core.BaseSequence
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.ReferencePoint
import io.repseq.core.VDJCGene
import io.repseq.dto.VDJCGeneData
import java.nio.file.Files
import java.util.*
import java.util.function.Supplier
import kotlin.collections.set

class AllelesBuilder(
    private val parameters: FindAllelesParameters,
    private val datasets: List<CloneReader>
) {
    private val VScoring = datasets[0].assemblerParameters.cloneFactoryParameters.vParameters.scoring
    private val JScoring = datasets[0].assemblerParameters.cloneFactoryParameters.jParameters.scoring

    fun sortClonotypes(): SortedClonotypes {
        // todo pre-build state, fill with references if possible
        val stateBuilder = PrimitivIOStateBuilder()
        val registeredGenes = mutableSetOf<String>()
        datasets.forEach { dataset ->
            IOUtil.registerGeneReferences(
                stateBuilder,
                dataset.usedGenes.filter { it.name !in registeredGenes },
                dataset.alignerParameters
            )
            registeredGenes += dataset.usedGenes.map { it.name }
        }


        // todo check memory budget
        // HDD-offloading collator of alignments
        // Collate solely by cloneId (no sorting by mapping type, etc.);
        // less fields to sort by -> faster the procedure
        val memoryBudget = if (Runtime.getRuntime().maxMemory() > 10000000000L /* -Xmx10g */) Runtime.getRuntime()
            .maxMemory() / 4L /* 1 Gb */ else 1 shl 28 /* 256 Mb */
        val sorterSupplier = ExceptionUtil.wrap { geneType: GeneType? ->
            HashSorter(
                Clone::class.java,
                { clone: Clone -> clone.getBestHit(geneType).gene.id.name.hashCode() },
                Comparator.comparing { clone: Clone -> clone.getBestHit(geneType).gene.id.name },
                5,
                Files.createTempFile("alleles.searcher", "hash.sorter"),
                8,
                8,
                stateBuilder.oState,
                stateBuilder.iState,
                memoryBudget,
                (
                    1 shl 18 /* 256 Kb */
                    ).toLong()
            )
        }
        val clonesSupplier = Supplier<OutputPort<Clone>> {
            FlatteningOutputPort(
                CUtils.asOutputPort(
                    datasets
                        .map { it.readClones() }
                        .map { port -> readClonesWithNonProductiveFilter(port) }
                        .map { port -> readClonesWithCountThreshold(port) }
                )
            )
        }
        return SortedClonotypes(
            sorterSupplier.apply(Variable).port(clonesSupplier.get()),
            sorterSupplier.apply(Joining).port(clonesSupplier.get())
        )
    }

    private fun readClonesWithNonProductiveFilter(port: OutputPort<Clone>): OutputPort<Clone> {
        // filter non-productive clonotypes
        return if (parameters.productiveOnly) {
            // todo CDR3?
            FilteringPort(port) { c: Clone -> !c.containsStops(GeneFeature.CDR3) && !c.isOutOfFrame(GeneFeature.CDR3) }
        } else {
            port
        }
    }

    private fun readClonesWithCountThreshold(port: OutputPort<Clone>): OutputPort<Clone> {
        return FilteringPort(port) { c: Clone -> c.count >= parameters.filterClonesWithCountLessThan }
    }

    fun buildClusters(sortedClones: OutputPortCloseable<Clone>, geneType: GeneType?): OutputPort<Cluster<Clone>> {
        val comparator = Comparator.comparing { c: Clone -> c.getBestHit(geneType).gene.id.name }

        // todo do not copy cluster
        val cluster: MutableList<Clone> = ArrayList()

        // group by similar V/J/C genes
        val result: OutputPortCloseable<Cluster<Clone>> = object : OutputPortCloseable<Cluster<Clone>> {
            override fun close() {
                sortedClones.close()
            }

            override fun take(): Cluster<Clone>? {
                while (true) {
                    val clone = sortedClones.take() ?: return null
                    if (cluster.isEmpty()) {
                        cluster.add(clone)
                        continue
                    }
                    val lastAdded = cluster[cluster.size - 1]
                    if (comparator.compare(lastAdded, clone) == 0) cluster.add(clone) else {
                        val copy = ArrayList(cluster)

                        // new cluster
                        cluster.clear()
                        cluster.add(clone)
                        return Cluster(copy)
                    }
                }
            }
        }
        return CUtils.wrapSynchronized(result)
    }

    private fun findAlleles(clusterByTheSameGene: Cluster<Clone>, geneType: GeneType): List<Allele> {
        require(clusterByTheSameGene.cluster.isNotEmpty())
        val commonAlignmentRanges = ClonesAlignmentRanges.commonAlignmentRanges(
            clusterByTheSameGene.cluster,
            parameters.minPortionOfClonesForCommonAlignmentRanges,
            geneType
        ) { it.getBestHit(geneType) }
        val bestHit = clusterByTheSameGene.cluster[0].getBestHit(geneType)
        val complimentaryGene = complimentaryGene(geneType)
        val cloneDescriptors = clusterByTheSameGene.cluster.asSequence()
            .filter { commonAlignmentRanges.containsClone(it) }
            .map { clone ->
                CloneDescription(
                    clone.getBestHit(geneType).alignments.asSequence()
                        .flatMap { it.absoluteMutations.asSequence() }
                        .filter { commonAlignmentRanges.containsMutation(it) }
                        .asMutations(NucleotideSequence.ALPHABET),
                    clone.getNFeature(GeneFeature.CDR3).size(),
                    clone.getBestHit(complimentaryGene).gene.geneName
                )
            }
            .toList()
        val allelesSearcher: AllelesSearcher = TIgGERAllelesSearcher()

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

    fun allelesGeneData(cluster: Cluster<Clone>, geneType: GeneType): List<VDJCGeneData> =
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
        val knownRanges: List<Range>
    ) {
        override fun toString(): String = "Allele{" +
            "id=" + gene.name +
            ", mutations=" + mutations +
            '}'
    }
}
