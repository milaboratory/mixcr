package com.milaboratory.mixcr.postanalysis.ui;

import com.milaboratory.mixcr.postanalysis.MetricValue;

import java.util.Objects;

/**
 *
 */
public class CharacteristicGroupResultCell<K> extends MetricValue<K> {
    /** id of the dataset */
    public final String datasetId;

    public CharacteristicGroupResultCell(K key, double value, String datasetId) {
        super(key, value);
        this.datasetId = datasetId;
    }

    @Override
    public String toString() {
        return key + "-" + datasetId + ":" + value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        CharacteristicGroupResultCell<?> that = (CharacteristicGroupResultCell<?>) o;
        return Objects.equals(datasetId, that.datasetId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), datasetId);
    }
}
