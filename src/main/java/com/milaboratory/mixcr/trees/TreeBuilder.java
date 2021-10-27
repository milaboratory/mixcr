package com.milaboratory.mixcr.trees;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPortCloseable;
import cc.redberry.pipe.blocks.FilteringPort;
import cc.redberry.pipe.util.FlatteningOutputPort;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneReader;
import com.milaboratory.primitivio.PrimitivIOStateBuilder;
import com.milaboratory.util.sorting.HashSorter;
import io.repseq.core.GeneFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 *
 */
public class TreeBuilder {
    final TreeBuilderParameters parameters;
    final ClusteringCriteria clusteringCriteria;
    final List<CloneReader> datasets;

    public TreeBuilder(TreeBuilderParameters parameters,
                       ClusteringCriteria clusteringCriteria,
                       List<CloneReader> datasets) throws IOException {
        this.parameters = parameters;
        this.clusteringCriteria = clusteringCriteria;
        this.datasets = datasets;

        // todo group v/j genes


    }

    OutputPortCloseable<CloneWrapper> sortClonotypes() throws IOException {
        // todo pre-build state, fill with references if possible
        PrimitivIOStateBuilder stateBuilder = new PrimitivIOStateBuilder();

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
                c -> clusteringCriteria.clusteringHashCode().applyAsInt(c.clone),
                Comparator.comparing(c -> c.clone, clusteringCriteria.clusteringComparator()),
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

    OutputPortCloseable<Cluster> buildClusters(OutputPortCloseable<CloneWrapper> sortedClones) {
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

    List<Tree> processCluster(Cluster cluster) {
//        cluster.cluster
//        cluster.cluster.sort();
        /// older -> younger
        /// muts  -> muts'

        return null;
    }

    private static final Comparator<Clone> mutationsNumberComparator = (c1, c2) ->
}
