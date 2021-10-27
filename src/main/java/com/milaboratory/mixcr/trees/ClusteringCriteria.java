package com.milaboratory.mixcr.trees;

import com.milaboratory.mixcr.basictypes.Clone;
import io.repseq.core.GeneType;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 *
 */
public interface ClusteringCriteria {
    /** Returns the hash code of the feature which is used to group clonotypes */
    ToIntFunction<Clone> clusteringHashCode();

    /** Comparator for clonotypes with the same hash code but from different clusters */
    Comparator<Clone> clusteringComparator();

    default ToIntFunction<Clone> fullHashCode() {
        return clone -> Arrays.hashCode(new int[]{
                clusteringHashCode().applyAsInt(clone),
                numberOfMutations(clone, GeneType.Variable),
                numberOfMutations(clone, GeneType.Joining)
        });
    }

    class DefaultClusteringCriteria implements ClusteringCriteria {
        @Override
        public ToIntFunction<Clone> clusteringHashCode() {
            return clone -> Objects.hash(
                    clone.getBestHitGene(GeneType.Variable).getId().getName(),
                    clone.getBestHitGene(GeneType.Joining).getId().getName());
        }

        @Override
        public Comparator<Clone> clusteringComparator() {
            return Comparator
                    .<Clone, String>comparing(c -> c.getBestHitGene(GeneType.Variable).getId().getName())
                    .thenComparing(c -> c.getBestHitGene(GeneType.Joining).getId().getName());
        }
    }

    /** Returns the number of mutations from the reference */
    static int numberOfMutations(Clone clone, GeneType geneType) {
        return Arrays.stream(clone.getBestHit(geneType).getAlignments())
                .mapToInt(al -> al.getAbsoluteMutations().size()).sum();
    }
}
