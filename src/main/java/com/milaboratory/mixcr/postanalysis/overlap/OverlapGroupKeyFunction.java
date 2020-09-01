package com.milaboratory.mixcr.postanalysis.overlap;

import java.util.List;

/**
 *
 */
public interface OverlapGroupKeyFunction<K, T> {
    K getKey(List<T> i1, List<T> i2);
}
