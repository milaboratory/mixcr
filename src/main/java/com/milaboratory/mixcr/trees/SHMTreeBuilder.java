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
import com.milaboratory.core.alignment.Alignment;
import com.milaboratory.core.mutations.Mutation;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneReader;
import com.milaboratory.mixcr.basictypes.IOUtil;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.primitivio.PrimitivIOStateBuilder;
import com.milaboratory.util.sorting.HashSorter;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

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
                        c -> !c.containsStops(GeneFeature.CDR3) && !c.isOutOfFrame(GeneFeature.CDR3));
            else
                port = datasets.get(i).readClones();

            wrapped.add((OutputPortCloseable<CloneWrapper>) CUtils.wrap(port, c -> new CloneWrapper(c, datasetId)));
        }

        return sorter.port(new FlatteningOutputPort<>(CUtils.asOutputPort(wrapped)));
    }

    public OutputPortCloseable<Cluster> buildClusters(OutputPortCloseable<CloneWrapper> sortedClones) {
        // todo do not copy cluster
        final List<CloneWrapper> cluster = new ArrayList<>();

        // group by similar V/J/C genes
        return new OutputPortCloseable<Cluster>() {
            @Override
            public void close() {
                sortedClones.close();
            }

            @Override
            public Cluster take() {
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

                        return new Cluster(copy);
                    }
                }
                return null;
            }
        };
    }

    public Collection<Tree<CloneWrapper>> processCluster(Cluster cluster) {
        /// older -> younger
        /// muts  -> muts'

        List<Tree<CloneWithMutationsDescriptor>> result = new ArrayList<>();

        for (CloneWrapper cloneWrapper : cluster.cluster) {
            addToTheTree(result, cloneWrapper);
        }
        return result.stream()
                .map(it -> it.map(CloneWithMutationsDescriptor::getCloneWrapper))
                .collect(Collectors.toList());
    }

    private void addToTheTree(List<Tree<CloneWithMutationsDescriptor>> result, CloneWrapper cloneWrapper) {
        MutationsDescriptor mutations = mutationsDescriptor(cloneWrapper.clone);
        if (mutations.mutations.isEmpty()) {
            //TODO fix when start to work with D section
            return;
        }
        CloneWithMutationsDescriptor cloneWithMutationsDescriptor = new CloneWithMutationsDescriptor(cloneWrapper, mutations);
        for (Tree<CloneWithMutationsDescriptor> tree : result) {
            if (mutations.contains(tree.getRoot().getContent().mutations)) {
                Tree.Node<CloneWithMutationsDescriptor> mostSimilarNode = tree.allNodes()
                        .max(Comparator.comparing(node -> node.getContent().mutations.sameMutationsCount(mutations)))
                        .orElseThrow(IllegalStateException::new);

                mostSimilarNode.addChild(cloneWithMutationsDescriptor, null);
                return;
            }
        }
        result.add(new Tree<>(new Tree.Node<>(cloneWithMutationsDescriptor)));
    }

    private MutationsDescriptor mutationsDescriptor(Clone clone) {
        HashSet<OneMutationDescriptor> mutationDescriptors = new HashSet<>();
        collectMutationDescriptors(clone, GeneType.Variable, mutationDescriptors);
        collectMutationDescriptors(clone, GeneType.Joining, mutationDescriptors);

        return new MutationsDescriptor(mutationDescriptors);
    }

    private void collectMutationDescriptors(Clone clone, GeneType geneType, Collection<OneMutationDescriptor> result) {
        //TODO check quality by scales
        VDJCHit hit = clone.getBestHit(geneType);

        Alignment<NucleotideSequence>[] alignments = hit.getAlignments();

        for (Alignment<NucleotideSequence> alignment : alignments) {
            if (alignment == null)
                continue;

            Mutations<NucleotideSequence> mutations = alignment.getAbsoluteMutations();

            for (int j = 0; j < mutations.size(); j++) {
                result.add(asDescriptor(mutations.getMutation(j), geneType));
            }
        }
    }

    private OneMutationDescriptor asDescriptor(int mutation, GeneType geneType) {
        return new OneMutationDescriptor(
                geneType,
                Mutation.getPosition(mutation),
                Mutation.getTo(mutation),
                determineType(mutation)
        );
    }

    private OneMutationDescriptor.Type determineType(int mutation) {
        if (Mutation.isInsertion(mutation)) {
            return OneMutationDescriptor.Type.INSERTION;
        } else if (Mutation.isDeletion(mutation)) {
            return OneMutationDescriptor.Type.DELETION;
        } else if (Mutation.isSubstitution(mutation)) {
            return OneMutationDescriptor.Type.SUBSTITUTION;
        } else {
            throw new IllegalArgumentException();
        }
    }

    private static class CloneWithMutationsDescriptor {
        private final CloneWrapper cloneWrapper;
        private final MutationsDescriptor mutations;

        public CloneWithMutationsDescriptor(CloneWrapper cloneWrapper, MutationsDescriptor mutations) {
            this.cloneWrapper = cloneWrapper;
            this.mutations = mutations;
        }

        public CloneWrapper getCloneWrapper() {
            return cloneWrapper;
        }
    }

    private static class MutationsDescriptor {
        private final HashSet<OneMutationDescriptor> mutations;

        private MutationsDescriptor(HashSet<OneMutationDescriptor> mutations) {
            this.mutations = mutations;
        }

        //TODO optimize: replace HashSet with sorted LinkedList
        boolean contains(MutationsDescriptor other) {
            return this.mutations.containsAll(other.mutations);
        }

        int sameMutationsCount(MutationsDescriptor other) {
            return (int) this.mutations.stream().filter(other.mutations::contains).count();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MutationsDescriptor that = (MutationsDescriptor) o;
            return Objects.equals(mutations, that.mutations);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mutations);
        }

        @Override
        public String toString() {
            return "MutationsDescriptor{" +
                    "mutations=" + mutations +
                    '}';
        }
    }

    private static class OneMutationDescriptor {
        private final GeneType geneType;
        private final int coordinate;
        private final byte replaceWith;
        private final Type type;

        public OneMutationDescriptor(GeneType geneType, int coordinate, byte replaceWith, Type type) {
            this.geneType = geneType;
            this.coordinate = coordinate;
            this.replaceWith = replaceWith;
            this.type = type;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OneMutationDescriptor that = (OneMutationDescriptor) o;
            return coordinate == that.coordinate && replaceWith == that.replaceWith && geneType == that.geneType && type == that.type;
        }

        @Override
        public int hashCode() {
            return Objects.hash(geneType, coordinate, replaceWith, type);
        }

        @Override
        public String toString() {
            return "OneMutationDescriptor{" +
                    "geneType=" + geneType +
                    ", coordinate=" + coordinate +
                    ", replaceWith=" + replaceWith +
                    ", type=" + type +
                    '}';
        }

        enum Type {
            DELETION,
            INSERTION,
            SUBSTITUTION
        }
    }
}
