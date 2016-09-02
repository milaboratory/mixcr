/*
 * Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
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
