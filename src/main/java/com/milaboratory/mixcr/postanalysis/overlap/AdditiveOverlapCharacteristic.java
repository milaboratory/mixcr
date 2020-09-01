package com.milaboratory.mixcr.postanalysis.overlap;

import com.milaboratory.mixcr.postanalysis.SetPreprocessor;
import com.milaboratory.mixcr.postanalysis.additive.AdditiveCharacteristic;
import com.milaboratory.mixcr.postanalysis.additive.AggregationType;

/**
 *
 */
public class AdditiveOverlapCharacteristic<K, T> extends AdditiveCharacteristic<OverlapKey<K>, OverlapGroup<T>> {
    public final int i1, i2;

    public AdditiveOverlapCharacteristic(String name,
                                         SetPreprocessor<OverlapGroup<T>> preprocessor,
                                         int i1, int i2,
                                         OverlapGroupKeyFunction<K, T> keyFunction,
                                         OverlapGroupWeightFunction<T> weight,
                                         OverlapGroupAdditiveMetric<T> metric,
                                         AggregationType aggType, boolean normalizeByKey) {
        super(name, preprocessor,
                group -> weight.weight(group.getBySample(i1), group.getBySample(i2)),
                group -> new OverlapKey<>(keyFunction.getKey(group.getBySample(i1), group.getBySample(i2)), i1, i2),
                group -> metric.compute(group.getBySample(i1), group.getBySample(i2)),
                aggType, normalizeByKey);
        this.i1 = i1;
        this.i2 = i2;
    }
}
