package com.milaboratory.mixcr.postanalysis.overlap;

import java.util.List;

/**
 *
 */
public interface OverlapGroupAdditiveMetric<T> {
    double compute(List<T> i1, List<T> i2);
}
