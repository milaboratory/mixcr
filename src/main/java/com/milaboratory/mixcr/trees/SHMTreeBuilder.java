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
public class SHMTreeBuilder {
    final SHMTreeBuilderParameters parameters;
    final ClusteringCriteria clusteringCriteria;
    final List<CloneReader> datasets;

    public SHMTreeBuilder(SHMTreeBuilderParameters parameters,
                          ClusteringCriteria clusteringCriteria,
                          List<CloneReader> datasets) throws IOException {
        this.parameters = parameters;
        this.clusteringCriteria = clusteringCriteria;
        this.datasets = datasets;

        // todo group v/j genes


    }

    public OutputPortCloseable<CloneWrapper> sortClonotypes() throws IOException {
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

    public List<Tree> processCluster(Cluster cluster) {
        /// older -> younger
        /// muts  -> muts'

        return null;
    }
}
