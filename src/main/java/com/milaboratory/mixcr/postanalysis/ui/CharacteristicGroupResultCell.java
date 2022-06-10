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
