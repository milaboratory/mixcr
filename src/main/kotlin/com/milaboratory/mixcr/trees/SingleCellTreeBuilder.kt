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
package com.milaboratory.mixcr.trees

import cc.redberry.pipe.OutputPort
import cc.redberry.pipe.util.asOutputPort
import cc.redberry.pipe.util.buffered
import cc.redberry.pipe.util.filter
import cc.redberry.pipe.util.flatMap
import cc.redberry.pipe.util.flatten
import cc.redberry.pipe.util.forEach
import cc.redberry.pipe.util.map
import cc.redberry.pipe.util.mapInParallelOrdered
import cc.redberry.pipe.util.mapNotNull
import cc.redberry.pipe.util.synchronized
import cc.redberry.pipe.util.toList
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.GeneFeatures
import com.milaboratory.mixcr.basictypes.tag.SequenceTagValue
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.util.VJPair
import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivIOStateBuilder
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.Serializer
import com.milaboratory.primitivio.annotations.Serializable
import com.milaboratory.primitivio.readList
import com.milaboratory.primitivio.readObjectRequired
import com.milaboratory.primitivio.writeCollection
import com.milaboratory.util.TempFileDest
import com.milaboratory.util.cached
import com.milaboratory.util.groupByOnDisk
import com.milaboratory.util.pairComparator
import com.milaboratory.util.sortOnDisk
import io.repseq.core.Chains
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable

class SingleCellTreeBuilder(
    private val parameters: SHMTreeBuilderParameters.SingleCell.SimpleClustering,
    private val stateBuilder: PrimitivIOStateBuilder,
    private val tempDest: TempFileDest,
    private val clonesFilter: SHMTreeBuilderOrchestrator.ClonesFilter,
    private val scoringSet: ScoringSet,
    private val assemblingFeatures: GeneFeatures,
    private val SHMTreeBuilder: SHMTreeBuilder
) {
    fun buildTrees(
        clones: OutputPort<CloneWithDatasetId>,
        tagsInfo: TagsInfo,
        threads: Int,
        resultWriter: (OutputPort<TreeWithMetaBuilder>) -> Unit
    ) {
        //TODO replace with specialized code
        val cellTageIndex = tagsInfo.first { it.type == TagType.Cell }.index

        clones
            .groupByCellBarcodes(cellTageIndex)
            .synchronized()
            .formCellGroups()
            .cached(
                tempDest.addSuffix("tree.builder.sc.cellGroups"),
                stateBuilder,
                blockSize = 100
            )
            .use { cache ->
                val cellBarcodesToGroupChainPair = mutableMapOf<CellBarcodeWithDatasetId, ChainPairKey>()
                cache.createPort()
                    .groupCellsByChainPairs()
                    //on resolving intersection prefer larger groups
                    .sortOnDisk(
                        comparator = Comparator.comparingInt<GroupOfCells> { it.cellBarcodes.size }.reversed(),
                        tempFile = tempDest.resolveFile("tree.builder.sc.sort_by_cell_barcodes_count"),
                        chunkSize = 1024 * 1024
                    ).use { sortedCellGroups ->
                        //decision about every barcode, to what pair of heavy and light chains it belongs
                        sortedCellGroups.forEach { cellGroup ->
                            val newBarcodes = cellGroup.cellBarcodes - cellBarcodesToGroupChainPair.keys
                            //TODO count and print how much was filtered
                            if (newBarcodes.size > 1) {
                                newBarcodes.forEach {
                                    cellBarcodesToGroupChainPair[it] = cellGroup.chainPairKey
                                }
                            }
                        }
                    }

                val clusterPredictor = ClustersBuilder.ClusterPredictorForCellGroup(
                    parameters.algorithm.maxNDNDistanceForHeavyChain,
                    parameters.algorithm.maxNDNDistanceForLightChain,
                    scoringSet
                )
                val clustersBuilder = when (parameters.algorithm) {
                    is SHMTreeBuilderParameters.ClusterizationAlgorithmForSingleCell.BronKerbosch ->
                        ClustersBuilder.BronKerbosch(clusterPredictor)

                    is SHMTreeBuilderParameters.ClusterizationAlgorithmForSingleCell.SingleLinkage ->
                        ClustersBuilder.SingleLinkage(clusterPredictor)
                }

                val result = cache.createPort()
                    //read clones with barcodes and group them accordingly to decisions
                    .clustersWithSameVJAndCDR3Length(cellBarcodesToGroupChainPair)
                    .flatMap { cellGroups ->
                        val rebasedChainPairs = groupDuplicatesAndRebaseFromGermline(cellGroups)
                        clustersBuilder.buildClusters(rebasedChainPairs).asOutputPort()
                    }
                    .buffered(1) //also make take() from upstream synchronized
                    .mapInParallelOrdered(threads) { clusterOfCells ->
                        //TODO build linked topology
                        //TODO what to do with the same clones that exists in several nodes because mutations was in other chain
                        listOf(
                            clusterOfCells.map { it.heavy },
                            clusterOfCells.map { it.light }
                        )
                            // regroup the same clones. There may be a case that cells differ by only one chain
                            .map { cluster ->
                                cluster
                                    .flatMap { it.cloneWrapper.clones }
                                    .groupBy { it.clone.targets.toList() }
                                    .values
                                    .map { theSameClones ->
                                        CloneWrapper(theSameClones, theSameClones.first().clone.asVJBase())
                                    }
                                    .map { it.rebaseFromGermline(assemblingFeatures) }
                            }
                            .filter { cluster ->
                                //filter clusters formed by the same clones (for example, light chain didn't mutate)
                                cluster.size > 1
                            }
                            .map { SHMTreeBuilder.buildATreeFromRoot(it) }
                    }
                    .flatten()

                resultWriter(result)
            }
    }

    private fun groupDuplicatesAndRebaseFromGermline(cellGroups: List<ChainPair>): List<ChainPairRebasedFromGermline> {
        val rebasedChainPairs = cellGroups
            //grouping clones pairs that have the same heavy and light clones but from different files or with different C
            .groupBy { chainPair ->
                listOf(
                    chainPair.heavy.clone.targets.toList(),
                    chainPair.light.clone.targets.toList()
                )
            }
            .values
            .map { chainPairs ->
                // remove duplicates in chains.
                // For example, there is maybe one light clone with one C and two heavy clones with different C
                val heavy = chainPairs.map { it.heavy }.distinctBy { it.id }
                val light = chainPairs.map { it.light }.distinctBy { it.id }
                CloneWrapper(heavy, heavy.first().clone.asVJBase()) to
                        CloneWrapper(light, light.first().clone.asVJBase())
            }
            .map { (heavy, light) ->
                ChainPairRebasedFromGermline(
                    heavy = heavy.rebaseFromGermline(assemblingFeatures),
                    light = light.rebaseFromGermline(assemblingFeatures)
                )
            }
        return rebasedChainPairs
    }

    private fun OutputPort<CellGroup>.clustersWithSameVJAndCDR3Length(
        cellBarcodesToGroupChainPair: Map<CellBarcodeWithDatasetId, ChainPairKey>
    ): OutputPort<List<ChainPair>> = filter { it.cellBarcode in cellBarcodesToGroupChainPair }
        .map { cellGroup ->
            val chainPairKey = cellBarcodesToGroupChainPair.getValue(cellGroup.cellBarcode)
            ChainPair(
                heavy = cellGroup.heavy
                    .map { CloneWithDatasetId(it, cellGroup.cellBarcode.datasetId) }
                    .first { it.clone.asVJBase() == chainPairKey.heavy },
                light = cellGroup.light
                    .map { CloneWithDatasetId(it, cellGroup.cellBarcode.datasetId) }
                    .first { it.clone.asVJBase() == chainPairKey.light }
            )
        }
        .groupByOnDisk(
            tempDest.addSuffix("tree.builder.sc.group_by_found_chain_pairs"),
            pairComparator(VJBase.comparator),
            stateBuilder
        ) { it.heavy.clone.asVJBase() to it.light.clone.asVJBase() }
        .map { it.toList() }

    private fun OutputPort<CellGroup>.groupCellsByChainPairs(): OutputPort<GroupOfCells> =
        flatMap { cellGroup ->
            //TODO alleles
            //represent every cellGroup as all possible combinations of heavy and light chains
            cellGroup.heavy.flatMap { heavy ->
                cellGroup.light.map { light ->
                    ChainPairKeyWithCellBarcode(
                        ChainPairKey(
                            heavy = heavy.asVJBase(),
                            light = light.asVJBase()
                        ),
                        cellGroup.cellBarcode
                    )
                }
            }.asOutputPort()
        }
            //group by chain pairs. Groups will have intersection
            .groupByOnDisk(
                tempDest.addSuffix("tree.builder.sc.group_cells_by_chain_pairs"),
                Comparator
                    .comparing({ (heavy, _): ChainPairKey -> heavy }, VJBase.comparator)
                    .thenComparing({ it.light }, VJBase.comparator),
                stateBuilder
            ) { it.chainPairKey }
            //we search for clusters, so we not need groups with size 1
            .filter { it.count > 1 }
            .map { it.toList() }
            .map { chainPairKeys ->
                GroupOfCells(
                    chainPairKeys.first().chainPairKey,
                    chainPairKeys.map { it.cellBarcode }
                )
            }

    private fun OutputPort<List<CloneAndCellTag>>.formCellGroups() =
        mapNotNull { group ->
            val heavy = group
                .filter { it.clone.getBestHit(Variable).gene.chains == Chains.IGH }
                .sortedByDescending { it.tagCount }
                .map { it.clone }
                .take(2)
            val light = group
                .filter { it.clone.getBestHit(Variable).gene.chains in arrayOf(Chains.IGK, Chains.IGL) }
                .sortedByDescending { it.tagCount }
                .map { it.clone }
                .take(2)
            //TODO count and print
            if (heavy.isEmpty() || light.isEmpty()) return@mapNotNull null
            CellGroup(heavy, light, group.first().cellBarcode)
        }
            .filter { cellGroup ->
                (cellGroup.heavy + cellGroup.light)
                    .map { it.asVJBase() }
                    .any { clonesFilter.match(it) }
            }

    private fun OutputPort<CloneWithDatasetId>.groupByCellBarcodes(cellTageIndex: Int) =
        filter { clonesFilter.matchForProductive(it.clone) }
            .filter { clonesFilter.countMatches(it.clone) }
            .flatMap { (clone, datasetId) ->
                clone.tagCount.tuples()
                    .map { tuple -> (tuple[cellTageIndex] as SequenceTagValue).value }
                    .groupingBy { it }.eachCount()
                    .filterValues { umiCount -> umiCount > 3 }
                    .entries
                    .map { (cellBarcode, umiCount) ->
                        CloneAndCellTag(clone, CellBarcodeWithDatasetId(cellBarcode, datasetId), umiCount)
                    }
                    .asOutputPort()
            }
            .groupByOnDisk(
                tempDest.addSuffix("tree.builder.sc.group_by_cell_barcodes"),
                CellBarcodeWithDatasetId.comparator,
                stateBuilder
            ) { it.cellBarcode }
            .map { it.toList() }
}

private fun Clone.asVJBase() = VJBase(
    VJPair(
        V = getBestHit(Variable).gene.id,
        J = getBestHit(Joining).gene.id
    ),
    ntLengthOf(GeneFeature.CDR3)
)

@Serializable(by = ChainPairKeyWithCellBarcode.SerializerImpl::class)
class ChainPairKeyWithCellBarcode(
    val chainPairKey: ChainPairKey,
    val cellBarcode: CellBarcodeWithDatasetId
) {
    class SerializerImpl : Serializer<ChainPairKeyWithCellBarcode> {
        override fun write(output: PrimitivO, obj: ChainPairKeyWithCellBarcode) {
            output.writeObject(obj.chainPairKey)
            output.writeObject(obj.cellBarcode)
        }

        override fun read(input: PrimitivI): ChainPairKeyWithCellBarcode {
            val chainPairKey = input.readObjectRequired<ChainPairKey>()
            val cellBarcode = input.readObjectRequired<CellBarcodeWithDatasetId>()
            return ChainPairKeyWithCellBarcode(
                chainPairKey,
                cellBarcode
            )
        }
    }
}

@Serializable(by = ChainPairKey.SerializerImpl::class)
data class ChainPairKey(
    val heavy: VJBase,
    val light: VJBase
) {
    class SerializerImpl : Serializer<ChainPairKey> {
        override fun write(output: PrimitivO, obj: ChainPairKey) {
            output.writeObject(obj.heavy)
            output.writeObject(obj.light)
        }

        override fun read(input: PrimitivI): ChainPairKey {
            val heavy = input.readObjectRequired<VJBase>()
            val light = input.readObjectRequired<VJBase>()
            return ChainPairKey(
                heavy,
                light
            )
        }
    }
}

class ChainPairRebasedFromGermline(
    val heavy: CloneWithMutationsFromVJGermline,
    val light: CloneWithMutationsFromVJGermline
)

@Serializable(by = ChainPair.SerializerImpl::class)
class ChainPair(
    val heavy: CloneWithDatasetId,
    val light: CloneWithDatasetId
) {
    init {
        check(heavy.datasetId == light.datasetId)
    }

    class SerializerImpl : Serializer<ChainPair> {
        override fun write(output: PrimitivO, obj: ChainPair) {
            output.writeObject(obj.heavy)
            output.writeObject(obj.light)
        }

        override fun read(input: PrimitivI): ChainPair {
            val heavy = input.readObjectRequired<CloneWithDatasetId>()
            val light = input.readObjectRequired<CloneWithDatasetId>()
            return ChainPair(
                heavy,
                light
            )
        }
    }
}

@Serializable(by = CellGroup.SerializerImpl::class)
class CellGroup(
    val heavy: List<Clone>,
    val light: List<Clone>,
    val cellBarcode: CellBarcodeWithDatasetId
) {

    class SerializerImpl : Serializer<CellGroup> {
        override fun write(output: PrimitivO, obj: CellGroup) {
            output.writeCollection(obj.heavy, PrimitivO::writeObject)
            output.writeCollection(obj.light, PrimitivO::writeObject)
            output.writeObject(obj.cellBarcode)
        }

        override fun read(input: PrimitivI): CellGroup {
            val heavy = input.readList<Clone> { readObjectRequired() }
            val light = input.readList<Clone> { readObjectRequired() }
            val cellBarcode = input.readObjectRequired<CellBarcodeWithDatasetId>()
            return CellGroup(
                heavy,
                light,
                cellBarcode
            )
        }
    }
}

@Serializable(by = CloneAndCellTag.SerializerImpl::class)
class CloneAndCellTag(
    val clone: Clone,
    val cellBarcode: CellBarcodeWithDatasetId,
    val tagCount: Int
) {

    class SerializerImpl : Serializer<CloneAndCellTag> {
        override fun write(output: PrimitivO, obj: CloneAndCellTag) {
            output.writeObject(obj.clone)
            output.writeObject(obj.cellBarcode)
            output.writeInt(obj.tagCount)
        }

        override fun read(input: PrimitivI): CloneAndCellTag {
            val clone = input.readObjectRequired<Clone>()
            val cellBarcode = input.readObjectRequired<CellBarcodeWithDatasetId>()
            val tagCount = input.readInt()
            return CloneAndCellTag(
                clone,
                cellBarcode,
                tagCount
            )
        }
    }
}

@Serializable(by = GroupOfCells.SerializerImpl::class)
class GroupOfCells(
    val chainPairKey: ChainPairKey,
    val cellBarcodes: List<CellBarcodeWithDatasetId>
) {
    class SerializerImpl : Serializer<GroupOfCells> {
        override fun write(output: PrimitivO, obj: GroupOfCells) {
            output.writeObject(obj.chainPairKey)
            output.writeCollection(obj.cellBarcodes, PrimitivO::writeObject)
        }

        override fun read(input: PrimitivI): GroupOfCells {
            val chainPairKey = input.readObjectRequired<ChainPairKey>()
            val cellBarcodes = input.readList<CellBarcodeWithDatasetId> { readObjectRequired() }
            return GroupOfCells(
                chainPairKey,
                cellBarcodes
            )
        }
    }
}

@Serializable(by = CellBarcodeWithDatasetId.SerializerImpl::class)
data class CellBarcodeWithDatasetId(
    val cellBarcode: NucleotideSequence,
    val datasetId: Int
) {
    companion object {
        val comparator: Comparator<CellBarcodeWithDatasetId> = Comparator
            .comparing { (cellBarcode, _): CellBarcodeWithDatasetId -> cellBarcode }
            .thenComparingInt { it.datasetId }
    }

    class SerializerImpl : Serializer<CellBarcodeWithDatasetId> {
        override fun write(output: PrimitivO, obj: CellBarcodeWithDatasetId) {
            output.writeObject(obj.cellBarcode)
            output.writeInt(obj.datasetId)
        }

        override fun read(input: PrimitivI): CellBarcodeWithDatasetId {
            val cellBarcode = input.readObjectRequired<NucleotideSequence>()
            val datasetId = input.readInt()
            return CellBarcodeWithDatasetId(
                cellBarcode,
                datasetId
            )
        }
    }
}
