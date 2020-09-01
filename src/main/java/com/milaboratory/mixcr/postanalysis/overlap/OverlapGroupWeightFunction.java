package com.milaboratory.mixcr.postanalysis.overlap;

import java.util.List;

/**
 *
 */
public interface OverlapGroupWeightFunction<T> {
    double weight(List<T> i1, List<T> i2);
}
