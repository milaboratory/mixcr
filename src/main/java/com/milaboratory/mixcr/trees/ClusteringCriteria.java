package com.milaboratory.mixcr.trees;

import com.milaboratory.mixcr.basictypes.Clone;

import java.util.Comparator;
import java.util.function.ToIntFunction;

/**
 *
 */
public interface ClusteringCriteria {
    ToIntFunction<Clone> clusteringHashCode();

    Comparator<Clone> getComparator();
}
