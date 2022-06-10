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
package com.milaboratory.mixcr.postanalysis.additive;


import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 *
 */
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AdditiveMetrics.Constant.class, name = "constant"),
        @JsonSubTypes.Type(value = AdditiveMetrics.GeneFeatureLength.class, name = "length"),
        @JsonSubTypes.Type(value = AdditiveMetrics.AddedNucleotides.class, name = "addedNucleotides"),
        @JsonSubTypes.Type(value = AdditiveMetrics.AAPropertyNormalized.class, name = "aaPropertyNormalized"),
        @JsonSubTypes.Type(value = AdditiveMetrics.AAPropertySum.class, name = "aaProperty"),
})
public interface AdditiveMetric<T> {
    double compute(T obj);

    static <T> AdditiveMetric<T> square(AdditiveMetric<T> metric) {
        return obj -> {
            double v = metric.compute(obj);
            return v * v;
        };
    }
}
