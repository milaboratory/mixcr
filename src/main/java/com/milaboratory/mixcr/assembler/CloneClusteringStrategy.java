/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.assembler;

import com.milaboratory.core.Range;
import com.milaboratory.core.clustering.Cluster;
import com.milaboratory.core.clustering.ClusteringStrategy;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.tree.NeighborhoodIterator;
import com.milaboratory.core.tree.TreeSearchParameters;

public class CloneClusteringStrategy implements ClusteringStrategy<CloneAccumulator, NucleotideSequence> {
    final CloneClusteringParameters parameters;
    final CloneAssembler cloneAssembler;

    public CloneClusteringStrategy(CloneClusteringParameters parameters, CloneAssembler cloneAssembler) {
        this.parameters = parameters.clone();
        this.cloneAssembler = cloneAssembler;
    }

    @Override
    public boolean canAddToCluster(Cluster<CloneAccumulator> cluster,
                                   CloneAccumulator minorObject,
                                   NeighborhoodIterator<NucleotideSequence,
                                           CloneAccumulator[]> iterator) {
        Mutations<NucleotideSequence> currentMutations = iterator.getCurrentMutations();
        if (!cluster.getHead().getSequence().isCompatible(minorObject.getSequence(), currentMutations))
            return false;
        Range[] nRegions = cluster.getHead().getNRegions();
        int nMismatches = parameters.getAllowedMutationsInNRegions();
        out:
        for (Range nRegion : nRegions)
            for (int i = 0; i < currentMutations.size(); i++)
                if (nRegion.containsBoundary(currentMutations.getPositionByIndex(i)))
                    if (--nMismatches < 0)
                        return false;
                    else continue out;
        return parameters.getClusteringFilter().allow(currentMutations, cluster.getHead().getCount(),
                minorObject.getCount(), cluster.getHead().getSequence())
                && cloneAssembler.extractSignature(cluster.getHead()).matchHits(
                minorObject);
    }

    @Override
    public TreeSearchParameters getSearchParameters() {
        return parameters.getSearchParameters();
    }

    @Override
    public int getMaxClusterDepth() {
        return parameters.getSearchDepth();
    }

    @Override
    public int compare(CloneAccumulator o1, CloneAccumulator o2) {
        return Long.compare(o1.getCount(), o2.getCount());
    }
}
