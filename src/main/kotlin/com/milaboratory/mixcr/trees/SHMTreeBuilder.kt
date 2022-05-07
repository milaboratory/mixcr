/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
 */
@file:Suppress("PrivatePropertyName", "LocalVariableName")

package com.milaboratory.mixcr.trees

import cc.redberry.pipe.CUtils
import cc.redberry.pipe.OutputPort
import cc.redberry.pipe.OutputPortCloseable
import cc.redberry.pipe.blocks.FilteringPort
import cc.redberry.pipe.util.FlatteningOutputPort
import com.milaboratory.core.alignment.AlignmentScoring
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.CloneReader
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.cli.BuildSHMTreeStep
import com.milaboratory.mixcr.trees.ClusterProcessor.CalculatedClusterInfo
import com.milaboratory.mixcr.util.Cluster
import com.milaboratory.mixcr.util.XSV
import com.milaboratory.primitivio.PrimitivIOStateBuilder
import com.milaboratory.util.sorting.HashSorter
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.ReferencePoint
import java.io.IOException
import java.io.PrintStream
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.stream.Collectors

/**
 *
 */
class SHMTreeBuilder(
    private val parameters: SHMTreeBuilderParameters,
    private val clusteringCriteria: ClusteringCriteria,
    private val datasets: List<CloneReader>
) {
    private val VScoring: AlignmentScoring<NucleotideSequence> =
        datasets[0].assemblerParameters.cloneFactoryParameters.vParameters.scoring
    private val JScoring: AlignmentScoring<NucleotideSequence> =
        datasets[0].assemblerParameters.cloneFactoryParameters.jParameters.scoring
    private var decisions: MutableMap<Int, MutableMap<VJBase, TreeWithMetaBuilder.DecisionInfo>> =
        ConcurrentHashMap()
    private var currentTrees: MutableMap<VJBase, List<TreeWithMetaBuilder.Snapshot>> = ConcurrentHashMap()
    private val idGenerators: MutableMap<VJBase, IdGenerator> = ConcurrentHashMap()
    private val calculatedClustersInfo: MutableMap<VJBase, CalculatedClusterInfo> = ConcurrentHashMap()

    fun cloneWrappersCount(): Int {
        var cloneWrappersCount = 0
        val unsortedClones = unsortedClonotypes()
        while (unsortedClones.take() != null) {
            cloneWrappersCount++
        }
        return cloneWrappersCount
    }

    @Throws(IOException::class)
    private fun createSorter(): HashSorter<CloneWrapper> {
        // todo pre-build state, fill with references if possible
        val stateBuilder = PrimitivIOStateBuilder()
        datasets.forEach(Consumer { dataset: CloneReader ->
            IOUtil.registerGeneReferences(
                stateBuilder,
                dataset.genes,
                dataset.alignerParameters
            )
        })


        // todo check memory budget
        // HDD-offloading collator of alignments
        // Collate solely by cloneId (no sorting by mapping type, etc.);
        // less fields to sort by -> faster the procedure
        val memoryBudget = if (Runtime.getRuntime().maxMemory() > 10000000000L /* -Xmx10g */) Runtime.getRuntime()
            .maxMemory() / 4L /* 1 Gb */ else 1 shl 28 /* 256 Mb */

        // todo move constants to parameters
        // creating sorter instance
        return HashSorter(
            CloneWrapper::class.java,
            clusteringCriteria.clusteringHashCode(),
            clusteringCriteria.clusteringComparatorWithNumberOfMutations(VScoring, JScoring),
            5,
            Files.createTempFile("tree.builder", "hash.sorter"),
            8,
            8,
            stateBuilder.oState,
            stateBuilder.iState,
            memoryBudget,
            (1 shl 18 /* 256 Kb */).toLong()
        )
    }

    private fun unsortedClonotypes(): OutputPort<CloneWrapper> {
        val wrapped: MutableList<OutputPort<CloneWrapper>> = ArrayList()
        for (i in datasets.indices) {
            // filter non-productive clonotypes
            val port = if (parameters.productiveOnly) // todo CDR3?
                FilteringPort(
                    datasets[i].readClones()
                ) { c: Clone -> !c.containsStops(GeneFeature.CDR3) && !c.isOutOfFrame(GeneFeature.CDR3) } else datasets[i].readClones()
            val wrap = CUtils.wrap(port) { c: Clone ->
                val VGeneNames = Arrays.stream(c.getHits(GeneType.Variable))
                    .map { VHit -> VHit.gene.name }
                    .collect(Collectors.toList())
                val JGeneNames = Arrays.stream(c.getHits(GeneType.Joining))
                    .map { JHit -> JHit.gene.name }
                    .collect(Collectors.toList())
                CUtils.asOutputPort(
                    VGeneNames.stream()
                        .flatMap { VGeneName ->
                            JGeneNames.stream()
                                .map { JGeneName ->
                                    CloneWrapper(
                                        c,
                                        i,
                                        VJBase(VGeneName, JGeneName, c.getNFeature(GeneFeature.CDR3).size())
                                    )
                                }
                        }
                        .filter { it.getFeature(GeneFeature.CDR3) != null }
                        .filter {
                            it.getFeature(GeneFeature(ReferencePoint.VEndTrimmed, ReferencePoint.JBeginTrimmed)) != null
                        }
                        .collect(Collectors.toList())
                )
            }
            wrapped.add(FlatteningOutputPort(wrap))
        }
        return FlatteningOutputPort(CUtils.asOutputPort(wrapped))
    }

    fun buildClusters(sortedClones: OutputPortCloseable<CloneWrapper>): OutputPort<Cluster<CloneWrapper>> {
        // todo do not copy cluster
        val cluster: MutableList<CloneWrapper> = ArrayList()

        // group by similar V/J genes
        val result: OutputPortCloseable<Cluster<CloneWrapper>> = object : OutputPortCloseable<Cluster<CloneWrapper>> {
            override fun close() {
                sortedClones.close()
            }

            override fun take(): Cluster<CloneWrapper>? {
                var clone: CloneWrapper
                while (true) {
                    clone = sortedClones.take() ?: return null
                    if (cluster.isEmpty()) {
                        cluster.add(clone)
                        continue
                    }
                    val lastAdded = cluster[cluster.size - 1]
                    if (clusteringCriteria.clusteringComparator()
                            .compare(lastAdded, clone) == 0
                    ) cluster.add(clone) else {
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

    @Throws(IOException::class)
    fun sortedClones(): OutputPortCloseable<CloneWrapper> {
        return createSorter().port(unsortedClonotypes())
    }

    fun makeDecisions(): Int {
        val clonesToRemove: MutableMap<VJBase, MutableSet<Int>> = HashMap()
        decisions.forEach { (cloneId, decisions) ->
            val chosenDecision: VJBase = ClusterProcessor.makeDecision(decisions)
            decisions.keys.stream()
                .filter { it != chosenDecision }
                .forEach { VJBase ->
                    clonesToRemove.computeIfAbsent(VJBase) { HashSet() }.add(cloneId)
                }
        }
        currentTrees = currentTrees.entries.stream()
            .collect(
                Collectors.toMap(
                    { (key, _) -> key },
                    { (key, value) ->
                        value.stream()
                            .map { snapshot ->
                                snapshot.excludeClones(
                                    clonesToRemove.getOrDefault(
                                        key, emptySet()
                                    )
                                )
                            }
                            .filter { snapshot -> snapshot.clonesAdditionHistory.size > 1 }
                            .collect(Collectors.toList())
                    })
            )
        val clonesWasAdded = decisions.size
        decisions = HashMap()
        return clonesWasAdded
    }

    fun treesCount(): Int =
        currentTrees.values.stream()
            .mapToInt { it.size }
            .sum()

    fun zeroStep(clusterBySameVAndJ: Cluster<CloneWrapper>, debug: PrintStream) {
        val VJBase = clusterVJBase(clusterBySameVAndJ)
        val clusterProcessor = buildClusterProcessor(clusterBySameVAndJ, VJBase)
        val result = clusterProcessor.buildTreeTopParts()
        currentTrees[VJBase] = result.snapshots
        result.decisions.forEach { (cloneId, decision) ->
            decisions.computeIfAbsent(
                cloneId
            ) { ConcurrentHashMap() }[VJBase] = decision
        }
        XSV.writeXSVBody(debug, result.nodesDebugInfo, DebugInfo.COLUMNS_FOR_XSV, ";")
    }

    fun zeroStep_monitorMemory(clusterBySameVAndJ: Cluster<CloneWrapper>, debug: PrintStream?) {
        val VJBase = clusterVJBase(clusterBySameVAndJ)
        //        if (!VJBase.toString().equals("VJBase{VGeneName='IGHV3-30*00', JGeneName='IGHJ3*00', cdr3length=45}")) {
        if (VJBase.toString() != "VJBase{VGeneName='IGHV3-30*00', JGeneName='IGHJ4*00', cdr3length=42}") {
            return
        }
        val runtime = Runtime.getRuntime()
        val clusterProcessor = buildClusterProcessor(clusterBySameVAndJ, VJBase)
        for (i in 0..4) {
            val begin = Instant.now()
            runtime.gc()
            try {
                Thread.sleep(100)
            } catch (e: InterruptedException) {
                throw RuntimeException(e)
            }
            println("begin $VJBase")
            val usedMemoryBefore = runtime.totalMemory() - runtime.freeMemory()
            clusterProcessor.buildTreeTopParts()
            val usedMemoryAfter = runtime.totalMemory() - runtime.freeMemory()
            println(
                Duration.between(begin, Instant.now())
                    .toString() + " " + (usedMemoryAfter - usedMemoryBefore) / 1024.0 / 1024.0
            )
        }
        System.exit(0)
        //        currentTrees.put(VJBase, result.getSnapshots());
//        result.getDecisions().forEach((cloneId, decision) ->
//                decisions.computeIfAbsent(cloneId, __ -> new ConcurrentHashMap<>()).put(VJBase, decision)
//        );
//        XSV.writeXSVBody(debug, result.getNodesDebugInfo(), DebugInfo.COLUMNS_FOR_XSV, ";");
    }

    fun applyStep(
        clusterBySameVAndJ: Cluster<CloneWrapper>,
        step: BuildSHMTreeStep,
        debugOfPreviousStep: PrintStream,
        debugOfCurrentStep: PrintStream
    ) {
        val VJBase = clusterVJBase(clusterBySameVAndJ)
        val clusterProcessor = buildClusterProcessor(clusterBySameVAndJ, VJBase)
        val currentTrees = clusterProcessor.restore(currentTrees[VJBase]!!)
        val debugInfos = clusterProcessor.debugInfos(currentTrees)
        XSV.writeXSVBody(debugOfPreviousStep, debugInfos, DebugInfo.COLUMNS_FOR_XSV, ";")
        val result = clusterProcessor.applyStep(step, currentTrees)
        this.currentTrees[VJBase] = result.snapshots
        result.decisions.forEach { (cloneId, decision) ->
            decisions.computeIfAbsent(cloneId) { ConcurrentHashMap() }[VJBase] = decision
        }
        XSV.writeXSVBody(debugOfCurrentStep, result.nodesDebugInfo, DebugInfo.COLUMNS_FOR_XSV, ";")
    }

    fun getResult(clusterBySameVAndJ: Cluster<CloneWrapper>, previousStepDebug: PrintStream): List<TreeWithMeta> {
        val VJBase = clusterVJBase(clusterBySameVAndJ)
        val clusterProcessor = buildClusterProcessor(clusterBySameVAndJ, VJBase)
        val currentTrees = clusterProcessor.restore(currentTrees[VJBase]!!)
        val debugInfos = clusterProcessor.debugInfos(currentTrees)
        XSV.writeXSVBody(previousStepDebug, debugInfos, DebugInfo.COLUMNS_FOR_XSV, ";")
        return currentTrees.stream()
            .filter { treeWithMetaBuilder -> treeWithMetaBuilder.clonesCount() >= parameters.hideTreesLessThanSize }
            .map { treeWithMetaBuilder ->
                TreeWithMeta(
                    treeWithMetaBuilder.buildResult(),
                    treeWithMetaBuilder.rootInfo,
                    treeWithMetaBuilder.treeId
                )
            }.collect(Collectors.toList())
    }

    private fun buildClusterProcessor(clusterBySameVAndJ: Cluster<CloneWrapper>, VJBase: VJBase): ClusterProcessor {
        return ClusterProcessor.build(
            parameters,
            VScoring,
            JScoring,
            clusterBySameVAndJ,
            getOrCalculateClusterInfo(VJBase, clusterBySameVAndJ),
            idGenerators.computeIfAbsent(VJBase) { IdGenerator() }
        )
    }

    private fun clusterVJBase(clusterBySameVAndJ: Cluster<CloneWrapper>): VJBase = clusterBySameVAndJ.cluster[0].VJBase

    private fun getOrCalculateClusterInfo(
        VJBase: VJBase,
        clusterBySameVAndJ: Cluster<CloneWrapper>
    ): CalculatedClusterInfo = calculatedClustersInfo.computeIfAbsent(VJBase) {
        ClusterProcessor.calculateClusterInfo(
            clusterBySameVAndJ,
            parameters.minPortionOfClonesForCommonAlignmentRanges
        )
    }
}
