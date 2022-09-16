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
package com.milaboratory.mixcr.postanalysis.preproc;

import com.fasterxml.jackson.annotation.*;
import com.milaboratory.mixcr.basictypes.Clone;
import io.repseq.core.Chains;
import io.repseq.core.GeneFeature;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 *
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = ElementPredicate.NoOutOfFrames.class, name = "noOOF"),
        @JsonSubTypes.Type(value = ElementPredicate.NoStops.class, name = "noStops"),
        @JsonSubTypes.Type(value = ElementPredicate.IncludeChains.class, name = "includesChains"),
})
public interface ElementPredicate<T> extends Predicate<T> {
    String id();

    static <T> ElementPredicate<T> mk(String id, Predicate<T> predicate) {
        return new ElementPredicate<T>() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public boolean test(T t) {
                return predicate.test(t);
            }
        };
    }

    @JsonAutoDetect
    final class NoOutOfFrames implements ElementPredicate<Clone> {
        @JsonProperty("feature")
        public final GeneFeature feature;

        @JsonCreator
        public NoOutOfFrames(@JsonProperty("feature") GeneFeature feature) {
            this.feature = feature;
        }

        @Override
        public String id() {
            return "OOF in " + GeneFeature.encode(feature);
        }

        @Override
        public boolean test(Clone clone) {
            return !clone.isOutOfFrameOrAbsent(feature);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NoOutOfFrames that = (NoOutOfFrames) o;
            return Objects.equals(feature, that.feature);
        }

        @Override
        public int hashCode() {
            return Objects.hash(feature);
        }
    }

    @JsonAutoDetect
    final class NoStops implements ElementPredicate<Clone> {
        @JsonProperty("feature")
        public final GeneFeature feature;

        @JsonCreator
        public NoStops(@JsonProperty("feature") GeneFeature feature) {
            this.feature = feature;
        }

        @Override
        public String id() {
            return "stops in " + GeneFeature.encode(feature);
        }

        @Override
        public boolean test(Clone clone) {
            return !clone.containsStopsOrAbsent(feature);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NoStops noStops = (NoStops) o;
            return Objects.equals(feature, noStops.feature);
        }

        @Override
        public int hashCode() {
            return Objects.hash(feature);
        }
    }

    final class IncludeChains extends ChainsFilter<Clone> implements ElementPredicate<Clone> {
        @JsonCreator
        public IncludeChains(@JsonProperty("chains") Set<Chains> chains,
                             @JsonProperty("allowChimeras") boolean allowChimeras) {
            super(chains, allowChimeras);
        }

        public IncludeChains(Chains chains, boolean allowChimeras) {
            super(Collections.singleton(chains), allowChimeras);
        }

        @Override
        public String id() {
            return toString();
        }
    }
}
