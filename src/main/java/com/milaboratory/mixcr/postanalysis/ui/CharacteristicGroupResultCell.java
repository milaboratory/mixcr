package com.milaboratory.mixcr.postanalysis.ui;

import com.milaboratory.mixcr.postanalysis.MetricValue;

import java.util.Objects;

/**
 *
 */
public class CharacteristicGroupResultCell<K> extends MetricValue<K> {
    public final int sampleIndex;
    public final int metricIndex;

    public CharacteristicGroupResultCell(K key, double value, int sampleIndex, int metricIndex) {
        super(key, value);
        this.sampleIndex = sampleIndex;
        this.metricIndex = metricIndex;
    }

    @Override
    public String toString() {
        return key + "-" + sampleIndex + ":" + metricIndex + ":" + value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        CharacteristicGroupResultCell<?> that = (CharacteristicGroupResultCell<?>) o;
        return sampleIndex == that.sampleIndex &&
                metricIndex == that.metricIndex;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), sampleIndex, metricIndex);
    }
}
