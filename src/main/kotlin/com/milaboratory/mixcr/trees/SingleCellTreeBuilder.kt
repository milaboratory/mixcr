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

import cc.redberry.pipe.CUtils
import cc.redberry.pipe.OutputPort
import cc.redberry.pipe.OutputPortCloseable
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mitool.pattern.search.BasicSerializer
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.tag.SequenceTagValue
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.util.VJPair
import com.milaboratory.primitivio.GroupingCriteria
import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivIOStateBuilder
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.annotations.Serializable
import com.milaboratory.primitivio.blocks.PrimitivIBlocks
import com.milaboratory.primitivio.blocks.PrimitivIOBlocksUtil
import com.milaboratory.primitivio.blocks.PrimitivOBlocks
import com.milaboratory.primitivio.filter
import com.milaboratory.primitivio.flatMap
import com.milaboratory.primitivio.flatten
import com.milaboratory.primitivio.forEach
import com.milaboratory.primitivio.groupBy
import com.milaboratory.primitivio.map
import com.milaboratory.primitivio.mapInParallel
import com.milaboratory.primitivio.mapNotNull
import com.milaboratory.primitivio.readList
import com.milaboratory.primitivio.readObjectRequired
import com.milaboratory.primitivio.sort
import com.milaboratory.primitivio.writeList
import com.milaboratory.util.TempFileDest
import io.repseq.core.Chains
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import org.apache.commons.io.FilenameUtils
import java.nio.file.Paths
import java.util.*
import kotlin.io.path.exists

class SingleCellTreeBuilder(
    private val stateBuilder: PrimitivIOStateBuilder,
    private val tempDest: TempFileDest,
    private val clonesFilter: SHMTreeBuilderOrchestrator.ClonesFilter,
    private val scoringSet: ScoringSet,
    private val assemblingFeatures: Array<GeneFeature>,
    private val threads: Int,
    private val SHMTreeBuilder: SHMTreeBuilder
) {
    fun buildTrees(
        clones: OutputPortCloseable<CloneWithDatasetId>,
        outputTreesPath: String,
        tagsInfo: TagsInfo,
        singleCellParams: SHMTreeBuilderParameters.SingleCell.SimpleClustering
    ): OutputPortCloseable<TreeWithMetaBuilder> {
        //        val cellGroupsFile = tempDest.addSuffix("tree.builder.sc").resolvePath("cellGroups")
        //TODO remove
        val cellGroupsFile = Paths.get("${FilenameUtils.removeExtension(outputTreesPath)}.temp")
        val blockSize = 100

        if (!cellGroupsFile.exists()) {
            //TODO replace with specialized code
            val cellTageIndex = tagsInfo.first { it.type == TagType.Cell }.index
            val grouped = clones.groupByCellBarcodes(cellTageIndex)
            val cellGroups = grouped.formCellGroups()
            val primitivO = PrimitivOBlocks<CellGroup>(
                4,
                stateBuilder.oState,
                blockSize,
                PrimitivIOBlocksUtil.fastLZ4Compressor()
            )
            //TODO use tee
            CUtils.drain(cellGroups, primitivO.newWriter(cellGroupsFile))
        }

        val primitivI = PrimitivIBlocks(CellGroup::class.java, 4, stateBuilder.iState)

        val sortedCellGroups = primitivI.newReader(cellGroupsFile, blockSize)
            .groupCellsByChainPairs()
            //on resolving intersection prefer larger groups
            .sort(
                tempDest.addSuffix("tree.builder.sc.sort_by_cell_barcodes_count"),
                Comparator.comparingInt<GroupOfCells> { it.cellBarcodes.size }.reversed()
            )

        //decision about every barcode, to what pair of heavy and light chains it belongs
        val cellBarcodesToGroupChainPair = mutableMapOf<CellBarcodeWithDatasetId, ChainPairKey>()
        sortedCellGroups.forEach { cellGroup ->
            val newBarcodes = cellGroup.cellBarcodes - cellBarcodesToGroupChainPair.keys
            //TODO count and print how much was filtered
            if (newBarcodes.size > 1) {
                newBarcodes.forEach {
                    cellBarcodesToGroupChainPair[it] = cellGroup.chainPairKey
                }
            }
        }

        val clusterPredictor = ClustersBuilder.ClusterPredictorForCellGroup(
            singleCellParams.algorithm.maxNDNDistanceForHeavyChain,
            singleCellParams.algorithm.maxNDNDistanceForLightChain,
            scoringSet
        )
        val clustersBuilder = when (singleCellParams.algorithm) {
            is SHMTreeBuilderParameters.ClusterizationAlgorithmForSingleCell.BronKerbosch ->
                ClustersBuilder.BronKerbosch(clusterPredictor)
            is SHMTreeBuilderParameters.ClusterizationAlgorithmForSingleCell.Hierarchical ->
                ClustersBuilder.Hierarchical(clusterPredictor) { datasetId }
        }
        return primitivI.newReader(cellGroupsFile, blockSize)
            //read clones with barcodes and group them accordingly to decisions
            .clustersWithSameVJAndCDR3Length(cellBarcodesToGroupChainPair)
            .flatMap { cellGroups ->
                clustersBuilder.buildClusters(cellGroups.map {
                    ChainPairRebasedFromGermline(
                        heavy = it.heavy.rebaseFromGermline(assemblingFeatures),
                        light = it.light.rebaseFromGermline(assemblingFeatures)
                    )
                })
            }
            .mapInParallel(threads) { cluster ->
                //TODO build linked topology
                //TODO what to do with the same clones that exists in several nodes because mutations was in other chain
                listOf(
                    cluster.map { it.heavy }.distinctBy { it.cloneWrapper.id },
                    cluster.map { it.light }.distinctBy { it.cloneWrapper.id }
                )
                    .filter { it.size > 1 }
                    .map { SHMTreeBuilder.buildATreeFromRoot(it) }
            }
            .flatten()
    }

    private fun OutputPort<CellGroup>.clustersWithSameVJAndCDR3Length(
        cellBarcodesToGroupChainPair: Map<CellBarcodeWithDatasetId, ChainPairKey>
    ): OutputPort<List<ChainPair>> = filter { it.cellBarcode in cellBarcodesToGroupChainPair }
        .map { cellGroup ->
            val chainPairKey = cellBarcodesToGroupChainPair.getValue(cellGroup.cellBarcode)
            ChainPair(
                heavy = cellGroup.heavy
                    .map { CloneWrapper(it, cellGroup.cellBarcode.datasetId, it.asVJBase()) }
                    .first { it.VJBase == chainPairKey.heavy },
                light = cellGroup.light
                    .map { CloneWrapper(it, cellGroup.cellBarcode.datasetId, it.asVJBase()) }
                    .first { it.VJBase == chainPairKey.light }
            )
        }
        .groupBy(
            stateBuilder,
            tempDest.addSuffix("tree.builder.sc.group_by_found_chain_pairs"),
            GroupingCriteria.groupBy(
                Comparator.comparing({ (first, _): Pair<VJBase, VJBase> -> first }, VJBase.comparator)
                    .thenComparing({ it.second }, VJBase.comparator)
            ) { it.heavy.VJBase to it.light.VJBase }
        )

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
            }
        }
            //group by chain pairs. Groups will have intersection
            .groupBy(
                stateBuilder,
                tempDest.addSuffix("tree.builder.sc.group_cells_by_chain_pairs"),
                object : GroupingCriteria<ChainPairKeyWithCellBarcode> {
                    override fun hashCodeForGroup(entity: ChainPairKeyWithCellBarcode): Int =
                        Objects.hash(entity.chainPairKey)

                    override val comparator: Comparator<ChainPairKeyWithCellBarcode> = Comparator
                        .comparing(
                            { it.chainPairKey },
                            Comparator
                                .comparing<ChainPairKey, VJBase>({ it.heavy }, VJBase.comparator)
                                .thenComparing({ it.light }, VJBase.comparator)
                        )
                }
            )
            //we search for clusters, so we not need groups with size 1
            .filter { it.size > 1 }
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
                .filter { it.clone.getBestHit(Variable).gene.chains.intersects(Chains.IGKL) }
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
        filter { (clone, _) ->
            clonesFilter.matchForProductive(clone)
        }
            .filter { (clone, _) -> clonesFilter.countMatches(clone) }
            .flatMap { (clone, datasetId) ->
                clone.tagCount.tuples()
                    .map { tuple -> (tuple[cellTageIndex] as SequenceTagValue).sequence }
                    .groupingBy { it }.eachCount()
                    .filterValues { umiCount -> umiCount > 3 }
                    .entries
                    .map { (cellBarcode, umiCount) ->
                        CloneAndCellTag(clone, CellBarcodeWithDatasetId(cellBarcode, datasetId), umiCount)
                    }
            }
            .groupBy(
                stateBuilder,
                tempDest.addSuffix("tree.builder.sc.group_by_cell_barcodes"),
                GroupingCriteria.groupBy(CellBarcodeWithDatasetId.comparator) { it.cellBarcode }
            )

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
    class SerializerImpl : BasicSerializer<ChainPairKeyWithCellBarcode>() {
        override fun write(output: PrimitivO, obj: ChainPairKeyWithCellBarcode) {
            output.writeObject(obj.chainPairKey)
            output.writeObject(obj.cellBarcode)
        }

        override fun read(input: PrimitivI): ChainPairKeyWithCellBarcode = ChainPairKeyWithCellBarcode(
            input.readObjectRequired(),
            input.readObjectRequired()
        )
    }
}

@Serializable(by = ChainPairKey.SerializerImpl::class)
data class ChainPairKey(
    val heavy: VJBase,
    val light: VJBase
) {
    class SerializerImpl : BasicSerializer<ChainPairKey>() {
        override fun write(output: PrimitivO, obj: ChainPairKey) {
            output.writeObject(obj.heavy)
            output.writeObject(obj.light)
        }

        override fun read(input: PrimitivI): ChainPairKey = ChainPairKey(
            input.readObjectRequired(),
            input.readObjectRequired()
        )
    }
}

class ChainPairRebasedFromGermline(
    val heavy: CloneWithMutationsFromVJGermline,
    val light: CloneWithMutationsFromVJGermline
) {
    val datasetId get() = heavy.cloneWrapper.id.datasetId
}

@Serializable(by = ChainPair.SerializerImpl::class)
class ChainPair(
    val heavy: CloneWrapper,
    val light: CloneWrapper
) {
    init {
        check(heavy.id.datasetId == light.id.datasetId)
    }

    class SerializerImpl : BasicSerializer<ChainPair>() {
        override fun write(output: PrimitivO, obj: ChainPair) {
            output.writeObject(obj.heavy)
            output.writeObject(obj.light)
        }

        override fun read(input: PrimitivI): ChainPair = ChainPair(
            input.readObjectRequired(),
            input.readObjectRequired()
        )
    }
}

@Serializable(by = CellGroup.SerializerImpl::class)
class CellGroup(
    val heavy: List<Clone>,
    val light: List<Clone>,
    val cellBarcode: CellBarcodeWithDatasetId
) {

    class SerializerImpl : BasicSerializer<CellGroup>() {
        override fun write(output: PrimitivO, obj: CellGroup) {
            output.writeList(obj.heavy)
            output.writeList(obj.light)
            output.writeObject(obj.cellBarcode)
        }

        override fun read(input: PrimitivI): CellGroup = CellGroup(
            input.readList(),
            input.readList(),
            input.readObjectRequired()
        )
    }
}

@Serializable(by = CloneAndCellTag.SerializerImpl::class)
class CloneAndCellTag(
    val clone: Clone,
    val cellBarcode: CellBarcodeWithDatasetId,
    val tagCount: Int
) {

    class SerializerImpl : BasicSerializer<CloneAndCellTag>() {
        override fun write(output: PrimitivO, obj: CloneAndCellTag) {
            output.writeObject(obj.clone)
            output.writeObject(obj.cellBarcode)
            output.writeInt(obj.tagCount)
        }

        override fun read(input: PrimitivI): CloneAndCellTag = CloneAndCellTag(
            input.readObjectRequired(),
            input.readObjectRequired(),
            input.readInt()
        )
    }
}

@Serializable(by = GroupOfCells.SerializerImpl::class)
class GroupOfCells(
    val chainPairKey: ChainPairKey,
    val cellBarcodes: List<CellBarcodeWithDatasetId>
) {
    class SerializerImpl : BasicSerializer<GroupOfCells>() {
        override fun write(output: PrimitivO, obj: GroupOfCells) {
            output.writeObject(obj.chainPairKey)
            output.writeList(obj.cellBarcodes)
        }

        override fun read(input: PrimitivI): GroupOfCells = GroupOfCells(
            input.readObjectRequired(),
            input.readList()
        )
    }
}

@Serializable(by = CellBarcodeWithDatasetId.SerializerImpl::class)
data class CellBarcodeWithDatasetId(
    val cellBarcode: NucleotideSequence,
    val datasetId: Int
) {
    companion object {
        val comparator: Comparator<CellBarcodeWithDatasetId> = Comparator
            .comparing<CellBarcodeWithDatasetId, NucleotideSequence> { it.cellBarcode }
            .thenComparingInt { it.datasetId }
    }

    class SerializerImpl : BasicSerializer<CellBarcodeWithDatasetId>() {
        override fun write(output: PrimitivO, obj: CellBarcodeWithDatasetId) {
            output.writeObject(obj.cellBarcode)
            output.writeInt(obj.datasetId)
        }

        override fun read(input: PrimitivI): CellBarcodeWithDatasetId = CellBarcodeWithDatasetId(
            input.readObjectRequired(),
            input.readInt()
        )
    }
}
