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
import cc.redberry.pipe.OutputPortCloseable;
import cc.redberry.pipe.blocks.FilteringPort;
import cc.redberry.pipe.util.FlatteningOutputPort;
import com.milaboratory.core.Range;
import com.milaboratory.core.alignment.AffineGapAlignmentScoring;
import com.milaboratory.core.alignment.Aligner;
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneReader;
import com.milaboratory.mixcr.basictypes.IOUtil;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.primitivio.PrimitivIOStateBuilder;
import com.milaboratory.util.sorting.HashSorter;
import io.repseq.core.GeneType;
import org.apache.commons.math3.util.Pair;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

import static com.milaboratory.mixcr.trees.ClusteringCriteria.getAbsoluteMutationsWithoutCDR3;
import static io.repseq.core.GeneFeature.CDR3;
import static io.repseq.core.GeneType.Joining;
import static io.repseq.core.GeneType.Variable;
import static io.repseq.core.ReferencePoint.CDR3Begin;
import static io.repseq.core.ReferencePoint.CDR3End;

/**
 *
 */
public class SHMTreeBuilder {
    private final SHMTreeBuilderParameters parameters;
    private final ClusteringCriteria clusteringCriteria;
    private final List<CloneReader> datasets;

    public SHMTreeBuilder(SHMTreeBuilderParameters parameters,
                          ClusteringCriteria clusteringCriteria,
                          List<CloneReader> datasets
    ) {
        this.parameters = parameters;
        this.clusteringCriteria = clusteringCriteria;
        this.datasets = datasets;

        // todo group v/j genes
    }

    public OutputPortCloseable<CloneWrapper> sortClonotypes() throws IOException {
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
        HashSorter<CloneWrapper> sorter = new HashSorter<>(CloneWrapper.class,
                c -> clusteringCriteria.clusteringHashCodeWithNumberOfMutations().applyAsInt(c.clone),
                Comparator.comparing(c -> c.clone, clusteringCriteria.clusteringComparatorWithNumberOfMutations()),
                5,
                Files.createTempFile("tree.builder", "hash.sorter"),
                8,
                8,
                stateBuilder.getOState(),
                stateBuilder.getIState(),
                memoryBudget,
                1 << 18 /* 256 Kb */
        );

        List<OutputPortCloseable<CloneWrapper>> wrapped = new ArrayList<>();
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

            wrapped.add((OutputPortCloseable<CloneWrapper>) CUtils.wrap(port, c -> new CloneWrapper(c, datasetId)));
        }

        return sorter.port(new FlatteningOutputPort<>(CUtils.asOutputPort(wrapped)));
    }

    public OutputPortCloseable<Cluster<CloneWrapper>> buildClusters(OutputPortCloseable<CloneWrapper> sortedClones) {
        // todo do not copy cluster
        final List<CloneWrapper> cluster = new ArrayList<>();

        // group by similar V/J/C genes
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
                    if (clusteringCriteria.clusteringComparator().compare(lastAdded.clone, clone.clone) == 0)
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

    /**
     * (1) Parameters that may be used to determinate belonging to one tree:
     * - Distance from germline by V and J mutations
     * - Distance between NDN segments of two clonotypes
     * - Distance between V and J segments of two clonotypes
     * <p>
     * On stage of clustering we can't use VEnd and JBegin marking because gyppermutation on P region affects accuracy.
     * While alignment in some cases it's not possible to determinate mutation of P segment from shorter V or J version and other N nucleotides.
     * So, there will be used CDR3 instead of NDN, VBegin-CDR3Begin instead V and CDR3End-JEnd instead J
     * <p>
     * Within the tree you may find D gene by max sum score on entire tree.
     * <p>
     * Algorithm:
     * 1. Clustering by (1)
     * 2. Build a tree for every cluster
     * 3. Add possible common ancestors
     * 4. Iterate over remain clonotypes and try to add them to build trees with possible ancestors. Try to merge trees
     */
    public Collection<Tree<CloneWrapper>> processCluster(Cluster<CloneWrapper> clusterBySameVAndJ) {
        List<CloneDescriptor> clones = clusterBySameVAndJ.cluster.stream()
                .map(cloneWrapper -> new CloneDescriptor(
                        getAbsoluteMutationsWithoutCDR3(cloneWrapper.clone, Joining),
                        getAbsoluteMutationsWithoutCDR3(cloneWrapper.clone, Variable),
                        cloneWrapper.clone.getNFeature(CDR3),
                        ntLengthOfWithoutCDR3(cloneWrapper.clone, Joining),
                        ntLengthOfWithoutCDR3(cloneWrapper.clone, Variable),
                        cloneWrapper
                ))
                .collect(Collectors.toList());

        List<Cluster.Builder<CloneDescriptor>> clusteredClones = new ArrayList<>();

        for (CloneDescriptor cloneDescriptor : clones) {
            Optional<Pair<Cluster.Builder<CloneDescriptor>, Double>> nearestCluster = clusteredClones.stream()
                    .map(cluster -> Pair.create(cluster, distanceToCluster(cloneDescriptor, cluster)))
                    .min(Comparator.comparing(Pair::getSecond));

            //TODO to parameters
            double threshold = 0.2;
            if (nearestCluster.isPresent() && nearestCluster.get().getSecond() < threshold) {
                nearestCluster.get().getFirst().add(cloneDescriptor);
            } else {
                Cluster.Builder<CloneDescriptor> builder = new Cluster.Builder<>();
                builder.add(cloneDescriptor);
                clusteredClones.add(builder);
            }
        }

        List<Tree<CloneWrapper>> result = new ArrayList<>();
        clusteredClones.forEach(clusterBuilder -> {
            // Build a tree for every cluster
            // determine D gene
            // fix marks of VEnd and JBegin
            // resort by mutations count
            // build by next neighbor

            Cluster<CloneDescriptor> cluster = clusterBuilder.build();
            Tree<CloneWrapper> tree = new Tree<>(new Tree.Node<>(cluster.cluster.get(0).cloneWrapper));
            if (cluster.cluster.size() > 1) {
                cluster.cluster.subList(1, cluster.cluster.size() - 1).forEach(clone -> tree.getRoot().addChild(clone.cloneWrapper, null));
            }
            result.add(tree);
        });
        return result;
    }

    private double distanceToCluster(CloneDescriptor cloneDescriptor, Cluster.Builder<CloneDescriptor> cluster) {
        return cluster.getCurrentCluster().stream()
                .mapToDouble(compareTo -> computeDistance(cloneDescriptor, compareTo))
                .min()
                .orElseThrow(IllegalArgumentException::new);
    }

    private double computeDistance(CloneDescriptor clone, CloneDescriptor compareWith) {
        Mutations<NucleotideSequence> VMutations = clone.VMutationsWithoutCDR3.invert().combineWith(compareWith.VMutationsWithoutCDR3);
        Mutations<NucleotideSequence> JMutations = clone.JMutationsWithoutCDR3.invert().combineWith(compareWith.JMutationsWithoutCDR3);

        Mutations<NucleotideSequence> CDR3Mutations = Aligner.alignGlobal(
                AffineGapAlignmentScoring.getNucleotideBLASTScoring(),
                clone.CDR3,
                compareWith.CDR3
        ).getAbsoluteMutations();

        double CDR3Length = (clone.CDR3.size() + compareWith.CDR3.size()) / 2.0;
        double VLength = (clone.VLengthWithoutCDR3 + compareWith.VLengthWithoutCDR3) / 2.0;
        double JLength = (clone.JLengthWithoutCDR3 + compareWith.JLengthWithoutCDR3) / 2.0;

        double normalizedDistanceFromCloneToGermline = (clone.VMutationsWithoutCDR3.size() + clone.JMutationsWithoutCDR3.size()) / (VLength + JLength);
        double normalizedDistanceFromCompareToGermline = (compareWith.VMutationsWithoutCDR3.size() + compareWith.JMutationsWithoutCDR3.size()) / (VLength + JLength);
        double normalizedAverageDistanceToGermline = (normalizedDistanceFromCloneToGermline + normalizedDistanceFromCompareToGermline) / 2.0;
        double normalizedDistanceBetweenClones = (VMutations.size() + JMutations.size() + CDR3Mutations.size()) / (VLength + JLength + CDR3Length);
        double normalizedDistanceBetweenClonesInCDR3 = (CDR3Mutations.size()) / CDR3Length;

        //TODO parameters
        return normalizedDistanceBetweenClonesInCDR3 + (normalizedDistanceBetweenClones - normalizedAverageDistanceToGermline);
    }

    private static int ntLengthOfWithoutCDR3(Clone clone, GeneType geneType) {
        VDJCHit bestHit = clone.getBestHit(geneType);
        Range CDR3Range = new Range(bestHit.getPosition(0, CDR3Begin), bestHit.getPosition(0, CDR3End));

        return Arrays.stream(bestHit.getAlignments())
                .map(Alignment::getSequence1Range)
                .flatMap(sequence1Range -> sequence1Range.without(CDR3Range).stream())
                .mapToInt(Range::length)
                .sum();
    }


    private static class CloneDescriptor {
        private final Mutations<NucleotideSequence> VMutationsWithoutCDR3;
        private final Mutations<NucleotideSequence> JMutationsWithoutCDR3;
        private final NucleotideSequence CDR3;
        private final int VLengthWithoutCDR3;
        private final int JLengthWithoutCDR3;
        private final CloneWrapper cloneWrapper;

        private CloneDescriptor(Mutations<NucleotideSequence> VMutationsWithoutCDR3,
                                Mutations<NucleotideSequence> JMutationsWithoutCDR3,
                                NucleotideSequence CDR3,
                                int VLengthWithoutCDR3,
                                int JLengthWithoutCDR3,
                                CloneWrapper cloneWrapper) {
            this.VMutationsWithoutCDR3 = VMutationsWithoutCDR3;
            this.JMutationsWithoutCDR3 = JMutationsWithoutCDR3;
            this.CDR3 = CDR3;
            this.VLengthWithoutCDR3 = VLengthWithoutCDR3;
            this.JLengthWithoutCDR3 = JLengthWithoutCDR3;
            this.cloneWrapper = cloneWrapper;
        }
    }
}
