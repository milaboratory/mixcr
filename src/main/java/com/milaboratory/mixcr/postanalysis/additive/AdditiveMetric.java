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
