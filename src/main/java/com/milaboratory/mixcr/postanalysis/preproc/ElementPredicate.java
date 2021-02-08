package com.milaboratory.mixcr.postanalysis.preproc;

import com.fasterxml.jackson.annotation.*;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapGroup;
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
        @JsonSubTypes.Type(value = ElementPredicate.OverlapIncludeChains.class, name = "overlapIncludesChains")
})
public interface ElementPredicate<T> extends Predicate<T> {
    default String description() {
        return "";
    }

    @JsonAutoDetect
    final class NoOutOfFrames implements ElementPredicate<Clone> {
        @Override
        public String description() {
            return "Exclude out-of-frames";
        }

        @Override
        public boolean test(Clone clone) {
            if (clone.isOutOfFrame(GeneFeature.CDR3))
                return false;
            return true;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(13);
        }
    }

    @JsonAutoDetect
    final class NoStops implements ElementPredicate<Clone> {
        @Override
        public String description() {
            return "Exclude stop-codons";
        }

        @Override
        public boolean test(Clone clone) {
            for (GeneFeature gf : clone.getParentCloneSet().getAssemblingFeatures()) {
                if (clone.containsStops(gf))
                    return false;
            }
            return true;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hash(13);
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

    final class OverlapIncludeChains implements ElementPredicate<OverlapGroup<Clone>> {
        @JsonProperty("chains")
        public final Chains chains;

        @JsonCreator
        public OverlapIncludeChains(@JsonProperty("chains") Chains chains) {
            this.chains = chains;
        }

        @Override
        public String description() {
            return "Select chains: " + chains;
        }
        
        @Override
        public boolean test(OverlapGroup<Clone> object) {
            for (GeneType gt : GeneType.VJC_REFERENCE)
                for (int i = 0; i < object.size(); i++)
                    for (Clone clone : object.getBySample(i))
                        if (clone != null && chains.intersects(clone.getTopChain(gt)))
                            return true;

            return false;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OverlapIncludeChains that = (OverlapIncludeChains) o;
            return Objects.equals(chains, that.chains);
        }

        @Override
        public int hashCode() {
            return Objects.hash(chains);
        }
    }
}
