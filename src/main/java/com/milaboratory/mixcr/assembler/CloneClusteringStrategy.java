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
package com.milaboratory.mixcr.assembler;

import com.milaboratory.core.Range;
import com.milaboratory.core.clustering.Cluster;
import com.milaboratory.core.clustering.ClusteringStrategy;
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.tree.NeighborhoodIterator;
import com.milaboratory.core.tree.TreeSearchParameters;
import com.milaboratory.mixcr.basictypes.tag.TagCountAggregator;

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

        double minimalTagSetOverlap = parameters.getMinimalTagSetOverlap();
        if (minimalTagSetOverlap > 0) {
            TagCountAggregator headTags = cluster.getHead().tagBuilder;
            TagCountAggregator minorTags = minorObject.tagBuilder;
            if (headTags.intersectionFractionOf(minorTags) >= minimalTagSetOverlap)
                return true;
        }

        Range[] nRegions = cluster.getHead().getNRegions();
        int nMismatches = parameters.getAllowedMutationsInNRegions();
        out:
        for (Range nRegion : nRegions)
            for (int i = 0; i < currentMutations.size(); i++)
                if (nRegion.containsBoundary(currentMutations.getPositionByIndex(i)))
                    if (--nMismatches < 0)
                        return false;
                    else continue out;
        return parameters.getClusteringFilter().allow(currentMutations,
                cluster.getHead().getWeight(), minorObject.getWeight(),
                cluster.getHead().getSequence())
                && cloneAssembler.extractSignature(cluster.getHead()).matchHits(minorObject);
    }

    @Override
    public TreeSearchParameters getSearchParameters(Cluster<CloneAccumulator> cluster) {
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
