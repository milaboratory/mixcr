package com.milaboratory.mixcr.postanalysis.preproc;

import com.fasterxml.jackson.annotation.*;
import com.milaboratory.mixcr.basictypes.Clone;
import io.repseq.core.Chains;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;

import java.util.Objects;
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
    default String description() {
        return "";
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
        public String description() {
            return "Exclude out-of-frames";
        }

        @Override
        public boolean test(Clone clone) {
            return !clone.isOutOfFrame(feature);
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
        public String description() {
            return "Exclude stop-codons in " + GeneFeature.encode(feature);
        }


        @Override
        public boolean test(Clone clone) {
            return !clone.containsStops(feature);
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

    final class IncludeChains implements ElementPredicate<Clone> {
        @JsonProperty("chains")
        public final Chains chains;

        @JsonCreator
        public IncludeChains(@JsonProperty("chains") Chains chains) {
            this.chains = chains;
        }

        @Override
        public String description() {
            return "Select chains: " + chains;
        }

        @Override
        public boolean test(Clone object) {
            for (GeneType gt : GeneType.VJC_REFERENCE)
                if (chains.intersects(object.getTopChain(gt)))
                    return true;

            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            IncludeChains that = (IncludeChains) o;
            return Objects.equals(chains, that.chains);
        }

        @Override
        public int hashCode() {
            return Objects.hash(chains);
        }
    }
}
