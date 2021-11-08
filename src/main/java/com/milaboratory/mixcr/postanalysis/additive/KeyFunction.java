package com.milaboratory.mixcr.postanalysis.additive;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.milaboratory.mixcr.postanalysis.spectratype.SpectratypeKeyFunction;

import java.util.function.Function;

/**
 *
 */
@JsonAutoDetect(
        fieldVisibility = JsonAutoDetect.Visibility.ANY,
        isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = KeyFunctions.Named.class, name = "named"),
        @JsonSubTypes.Type(value = KeyFunctions.SegmentUsage.class, name = "segment"),
        @JsonSubTypes.Type(value = KeyFunctions.GeneUsage.class, name = "gene"),
        @JsonSubTypes.Type(value = KeyFunctions.FamiltyUsage.class, name = "family"),
        @JsonSubTypes.Type(value = KeyFunctions.VJSegmentUsage.class, name = "vjSegments"),
        @JsonSubTypes.Type(value = KeyFunctions.VJGeneUsage.class, name = "vjGenes"),
        @JsonSubTypes.Type(value = KeyFunctions.VJFamilyUsage.class, name = "vjFamilies"),
        @JsonSubTypes.Type(value = KeyFunctions.IsotypeUsage.class, name = "isotype"),
        @JsonSubTypes.Type(value = KeyFunctions.NTFeature.class, name = "ntFeature"),
        @JsonSubTypes.Type(value = KeyFunctions.AAFeature.class, name = "aaFeature"),
        @JsonSubTypes.Type(value = KeyFunctions.Tuple2Key.class, name = "tuple"),
        @JsonSubTypes.Type(value = SpectratypeKeyFunction.class, name = "spectratype"),
})
public interface KeyFunction<K, T> {
    K getKey(T obj);

    default <K1> KeyFunction<K1, T> map(Function<K, K1> mapper) {
        return obj -> mapper.apply(KeyFunction.this.getKey(obj));
    }
}
