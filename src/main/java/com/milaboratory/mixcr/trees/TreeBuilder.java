package com.milaboratory.mixcr.trees;

import cc.redberry.pipe.CUtils;
import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.OutputPortCloseable;
import cc.redberry.pipe.util.FlatteningOutputPort;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneReader;
import com.milaboratory.primitivio.PrimitivIOStateBuilder;
import com.milaboratory.util.sorting.HashSorter;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 *
 */
public class TreeBuilder {
    final ClusteringCriteria clusteringCriteria;
    final List<CloneReader> datasets;


    public TreeBuilder(ClusteringCriteria clusteringCriteria,
                       List<CloneReader> datasets) throws IOException {
        this.clusteringCriteria = clusteringCriteria;
        this.datasets = datasets;

        // todo pre-build state, fill with references
        PrimitivIOStateBuilder stateBuilder = new PrimitivIOStateBuilder();

        // HDD-offloading collator of alignments
        // Collate solely by cloneId (no sorting by mapping type, etc.);
        // less fields to sort by -> faster the procedure
        long memoryBudget =
                Runtime.getRuntime().maxMemory() > 10_000_000_000L /* -Xmx10g */
                        ? Runtime.getRuntime().maxMemory() / 4L /* 1 Gb */
                        : 1 << 28 /* 256 Mb */;

        HashSorter<CloneWrapper> sorter = new HashSorter<CloneWrapper>(CloneWrapper.class,
                c -> clusteringCriteria.clusteringHashCode().applyAsInt(c.clone),
                Comparator.<CloneWrapper, Clone>comparing(c -> c.clone, clusteringCriteria.getComparator()).thenComparing(c -> c.datasetId),
                5,
                Files.createTempFile("tree.builder", "hash.sorter"),
                8,
                8,
                stateBuilder.getOState(),
                stateBuilder.getIState(),
                memoryBudget,
                1 << 18 /* 256 Kb */
        );

        List<OutputPortCloseable<CloneWrapper>> wrapperd = new ArrayList<>();

        for (int i = 0; i < datasets.size(); i++) {
            CloneReader cloneReader = datasets.get(i);
            int datasetId = i;
            wrapperd.add((OutputPortCloseable<CloneWrapper>) CUtils.wrap(cloneReader.readClones(), c -> new CloneWrapper(c, datasetId)))
        }

        OutputPortCloseable<CloneWrapper> port = sorter.port(new FlatteningOutputPort<>(CUtils.asOutputPort(wrapperd)));
    }


    public List<Cluster> buildClusters() {
        // group by V/J/C genes

        OutputPort<List<Clone>> groups = null;

        List<Clone> group;
        while ((group = groups.take()) != null) {
            ///////

            //50-60
            //60-70

        }

        return null;
    }

    public List<Tree> processCluster(Cluster cluster) {

        return null;
    }


}
