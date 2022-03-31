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
package com.milaboratory.mixcr.trees;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.OutputPortCloseable;
import cc.redberry.pipe.blocks.FilteringPort;
import cc.redberry.pipe.util.FlatteningOutputPort;
import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneReader;
import com.milaboratory.mixcr.basictypes.IOUtil;
import com.milaboratory.mixcr.cli.BuildSHMTreeStep;
import com.milaboratory.mixcr.trees.ClusterProcessor.StepResult;
import com.milaboratory.mixcr.trees.TreeWithMetaBuilder.DecisionInfo;
import com.milaboratory.mixcr.util.Cluster;
import com.milaboratory.mixcr.util.XSV;
import com.milaboratory.primitivio.PrimitivIOStateBuilder;
import com.milaboratory.util.sorting.HashSorter;
import io.repseq.core.GeneFeature;
import io.repseq.core.ReferencePoint;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import static io.repseq.core.GeneFeature.CDR3;
import static io.repseq.core.GeneType.Joining;
import static io.repseq.core.GeneType.Variable;

/**
 *
 */
public class SHMTreeBuilder {
    private final SHMTreeBuilderParameters parameters;
    private final ClusteringCriteria clusteringCriteria;
    private final List<CloneReader> datasets;
    private final AlignmentScoring<NucleotideSequence> VScoring;
    private final AlignmentScoring<NucleotideSequence> JScoring;
    private Map<Integer, Map<VJBase, DecisionInfo>> decisions = new HashMap<>();
    private Map<VJBase, List<TreeWithMetaBuilder.Snapshot>> currentTrees = new HashMap<>();
    private final Map<VJBase, IdGenerator> idGenerators = new HashMap<>();
    private final Map<VJBase, ClusterProcessor.CalculatedClusterInfo> calculatedClustersInfo = new HashMap<>();

    public SHMTreeBuilder(SHMTreeBuilderParameters parameters,
                          ClusteringCriteria clusteringCriteria,
                          List<CloneReader> datasets
    ) {
        this.parameters = parameters;
        this.clusteringCriteria = clusteringCriteria;
        this.datasets = datasets;
        VScoring = datasets.get(0).getAssemblerParameters().getCloneFactoryParameters().getVParameters().getScoring();
        JScoring = datasets.get(0).getAssemblerParameters().getCloneFactoryParameters().getJParameters().getScoring();
    }

    public int cloneWrappersCount() {
        int cloneWrappersCount = 0;
        OutputPort<CloneWrapper> unsortedClones = unsortedClonotypes();
        while (unsortedClones.take() != null) {
            cloneWrappersCount++;
        }
        return cloneWrappersCount;
    }

    private HashSorter<CloneWrapper> createSorter() throws IOException {
        // todo pre-build state, fill with references if possible
        PrimitivIOStateBuilder stateBuilder = new PrimitivIOStateBuilder();

        datasets.forEach(dataset -> IOUtil.registerGeneReferences(stateBuilder, dataset.getGenes(), dataset.getAlignerParameters()));


        // todo check memory budget
        // HDD-offloading collator of alignments
        // Collate solely by cloneId (no sorting by mapping type, etc.);
        // less fields to sort by -> faster the procedure
        long memoryBudget =
                Runtime.getRuntime().maxMemory() > 10_000_000_000L /* -Xmx10g */
                        ? Runtime.getRuntime().maxMemory() / 4L /* 1 Gb */
                        : 1 << 28 /* 256 Mb */;

        // todo move constants to parameters
        // creating sorter instance
        return new HashSorter<>(CloneWrapper.class,
                clusteringCriteria.clusteringHashCode(),
                clusteringCriteria.clusteringComparatorWithNumberOfMutations(VScoring, JScoring),
                5,
                Files.createTempFile("tree.builder", "hash.sorter"),
                8,
                8,
                stateBuilder.getOState(),
                stateBuilder.getIState(),
                memoryBudget,
                1 << 18 /* 256 Kb */
        );
    }

    private OutputPort<CloneWrapper> unsortedClonotypes() {
        List<OutputPort<CloneWrapper>> wrapped = new ArrayList<>();
        for (int i = 0; i < datasets.size(); i++) {
            int datasetId = i;
            // filter non-productive clonotypes

            OutputPortCloseable<Clone> port;
            if (parameters.productiveOnly)
                // todo CDR3?
                port = new FilteringPort<>(datasets.get(i).readClones(),
                        c -> !c.containsStops(CDR3) && !c.isOutOfFrame(CDR3));
            else
                port = datasets.get(i).readClones();

            OutputPort<OutputPort<CloneWrapper>> wrap = CUtils.wrap(port, c -> {
                List<String> VGeneNames = Arrays.stream(c.getHits(Variable))
                        .map(VHit -> VHit.getGene().getName())
                        .collect(Collectors.toList());
                List<String> JGeneNames = Arrays.stream(c.getHits(Joining))
                        .map(JHit -> JHit.getGene().getName())
                        .collect(Collectors.toList());
                return CUtils.asOutputPort(
                        VGeneNames.stream()
                                .flatMap(VGeneName -> JGeneNames.stream()
                                        .map(JGeneName -> new CloneWrapper(c, datasetId, new VJBase(VGeneName, JGeneName, c.getNFeature(CDR3).size())))
                                )
                                .filter(it -> it.getFeature(new GeneFeature(ReferencePoint.VEndTrimmed, ReferencePoint.JBeginTrimmed)) != null)
                                .collect(Collectors.toList())
                );
            });
            wrapped.add(new FlatteningOutputPort<>(wrap));
        }

        return new FlatteningOutputPort<>(CUtils.asOutputPort(wrapped));
    }

    public OutputPortCloseable<Cluster<CloneWrapper>> buildClusters(OutputPortCloseable<CloneWrapper> sortedClones) {
        // todo do not copy cluster
        final List<CloneWrapper> cluster = new ArrayList<>();

        // group by similar V/J genes
        return new OutputPortCloseable<Cluster<CloneWrapper>>() {
            @Override
            public void close() {
                sortedClones.close();
            }

            @Override
            public Cluster<CloneWrapper> take() {
                CloneWrapper clone;
                while ((clone = sortedClones.take()) != null) {
                    if (cluster.isEmpty()) {
                        cluster.add(clone);
                        continue;
                    }

                    CloneWrapper lastAdded = cluster.get(cluster.size() - 1);
                    if (clusteringCriteria.clusteringComparator().compare(lastAdded, clone) == 0)
                        cluster.add(clone);
                    else {
                        ArrayList<CloneWrapper> copy = new ArrayList<>(cluster);

                        // new cluster
                        cluster.clear();
                        cluster.add(clone);

                        return new Cluster<>(copy);
                    }
                }
                return null;
            }
        };
    }

    public OutputPortCloseable<CloneWrapper> sortedClones() throws IOException {
        return createSorter().port(unsortedClonotypes());
    }

    public int makeDecisions() {
        Map<VJBase, Set<Integer>> clonesToRemove = new HashMap<>();

        decisions.forEach((cloneId, decisions) -> {
            VJBase chosenDecision = ClusterProcessor.makeDecision(decisions);
            decisions.keySet().stream()
                    .filter(it -> !Objects.equals(it, chosenDecision))
                    .forEach(VJBase -> clonesToRemove.computeIfAbsent(VJBase, __ -> new HashSet<>()).add(cloneId));
        });
        currentTrees = currentTrees.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().stream()
                                .map(snapshot -> snapshot.excludeClones(clonesToRemove.getOrDefault(e.getKey(), Collections.emptySet())))
                                .filter(snapshot -> snapshot.getClonesAdditionHistory().size() > 1)
                                .collect(Collectors.toList()))
                );
        int clonesWasAdded = decisions.size();
        decisions = new HashMap<>();
        return clonesWasAdded;
    }

    public int treesCount() {
        return currentTrees.values().stream().mapToInt(List::size).sum();
    }

    public void zeroStep(Cluster<CloneWrapper> clusterBySameVAndJ, PrintStream debug) {
        VJBase VJBase = clusterVJBase(clusterBySameVAndJ);
        ClusterProcessor clusterProcessor = buildClusterProcessor(clusterBySameVAndJ, VJBase);
        StepResult result = clusterProcessor.buildTreeTopParts();
        currentTrees.put(VJBase, result.getSnapshots());
        result.getDecisions().forEach((cloneId, decision) ->
                decisions.computeIfAbsent(cloneId, __ -> new HashMap<>()).put(VJBase, decision)
        );
        XSV.writeXSVBody(debug, result.getNodesDebugInfo(), DebugInfo.COLUMNS_FOR_XSV, ";");
    }

    public void applyStep(
            Cluster<CloneWrapper> clusterBySameVAndJ,
            BuildSHMTreeStep step,
            PrintStream debugOfPreviousStep,
            PrintStream debugOfCurrentStep
    ) {
        VJBase VJBase = clusterVJBase(clusterBySameVAndJ);
        ClusterProcessor clusterProcessor = buildClusterProcessor(clusterBySameVAndJ, VJBase);
        List<TreeWithMetaBuilder> currentTrees = clusterProcessor.restore(this.currentTrees.get(VJBase));
        List<DebugInfo> debugInfos = clusterProcessor.debugInfos(currentTrees);
        XSV.writeXSVBody(debugOfPreviousStep, debugInfos, DebugInfo.COLUMNS_FOR_XSV, ";");


        StepResult result = clusterProcessor.applyStep(step, currentTrees);
        this.currentTrees.put(VJBase, result.getSnapshots());
        result.getDecisions().forEach((cloneId, decision) ->
                decisions.computeIfAbsent(cloneId, __ -> new HashMap<>()).put(VJBase, decision)
        );
        XSV.writeXSVBody(debugOfCurrentStep, result.getNodesDebugInfo(), DebugInfo.COLUMNS_FOR_XSV, ";");
    }

    public List<TreeWithMeta> getResult(Cluster<CloneWrapper> clusterBySameVAndJ, PrintStream previousStepDebug) {
        VJBase VJBase = clusterVJBase(clusterBySameVAndJ);
        ClusterProcessor clusterProcessor = buildClusterProcessor(clusterBySameVAndJ, VJBase);
        List<TreeWithMetaBuilder> currentTrees = clusterProcessor.restore(this.currentTrees.get(VJBase));
        List<DebugInfo> debugInfos = clusterProcessor.debugInfos(currentTrees);
        XSV.writeXSVBody(previousStepDebug, debugInfos, DebugInfo.COLUMNS_FOR_XSV, ";");
        return currentTrees.stream()
                .filter(treeWithMetaBuilder -> treeWithMetaBuilder.clonesCount() >= parameters.hideTreesLessThanSize)
                .map(treeWithMetaBuilder -> new TreeWithMeta(
                        treeWithMetaBuilder.buildResult(),
                        treeWithMetaBuilder.getRootInfo(),
                        treeWithMetaBuilder.getTreeId()
                )).collect(Collectors.toList());
    }

    private ClusterProcessor buildClusterProcessor(Cluster<CloneWrapper> clusterBySameVAndJ, VJBase VJBase) {
        return ClusterProcessor.build(
                parameters,
                VScoring,
                JScoring,
                clusterBySameVAndJ,
                getOrCalculateClusterInfo(VJBase, clusterBySameVAndJ),
                idGenerators.computeIfAbsent(VJBase, __ -> new IdGenerator())
        );
    }

    private VJBase clusterVJBase(Cluster<CloneWrapper> clusterBySameVAndJ) {
        return clusterBySameVAndJ.cluster.get(0).VJBase;
    }

    private ClusterProcessor.CalculatedClusterInfo getOrCalculateClusterInfo(VJBase VJBase, Cluster<CloneWrapper> clusterBySameVAndJ) {
        return calculatedClustersInfo.computeIfAbsent(
                VJBase,
                __ -> ClusterProcessor.calculateClusterInfo(clusterBySameVAndJ, parameters.minPortionOfClonesForCommonAlignmentRanges)
        );
    }
}
