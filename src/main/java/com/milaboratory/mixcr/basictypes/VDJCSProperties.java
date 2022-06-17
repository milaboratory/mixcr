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
package com.milaboratory.mixcr.basictypes;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.milaboratory.core.sequence.AminoAcidSequence;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.primitivio.annotations.Serializable;
import com.milaboratory.util.GlobalObjectMappers;
import com.milaboratory.util.sorting.*;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGeneId;

import java.util.*;

import static com.milaboratory.util.sorting.SortingPropertyRelation.*;

public class VDJCSProperties {
    public static final CloneOrdering CO_BY_COUNT = new CloneOrdering(new CloneCount());

    public static CloneOrdering cloneOrderingByAminoAcid(GeneFeature[] geneFeatures,
                                                         GeneType... segments) {
        return new CloneOrdering(orderingByAminoAcid(geneFeatures, segments));
    }

    public static CloneOrdering cloneOrderingByNucleotide(GeneFeature[] geneFeatures,
                                                          GeneType... segments) {
        return new CloneOrdering(orderingByNucleotide(geneFeatures, segments));
    }

    public static List<VDJCSProperties.VDJCSProperty<VDJCObject>> orderingByAminoAcid(
            GeneFeature[] geneFeatures, GeneType... segments) {
        List<VDJCSProperties.VDJCSProperty<VDJCObject>> result = new ArrayList<>();
        for (GeneFeature geneFeature : geneFeatures)
            result.add(new AASequence(geneFeature));
        for (GeneType segment : segments)
            result.add(new VDJCSegment(segment));
        return result;
    }

    public static List<VDJCSProperties.VDJCSProperty<VDJCObject>> orderingByNucleotide(
            GeneFeature[] geneFeatures, GeneType... segments) {
        List<VDJCSProperties.VDJCSProperty<VDJCObject>> result = new ArrayList<>();
        for (GeneFeature geneFeature : geneFeatures)
            result.add(new AASequence(geneFeature));
        for (GeneFeature geneFeature : geneFeatures)
            result.add(new NSequence(geneFeature));
        for (GeneType segment : segments)
            result.add(new VDJCSegment(segment));
        return result;
    }

    @Serializable(asJson = true)
    public static final class CloneOrdering {
        @JsonProperty("properties")
        private final VDJCSProperties.VDJCSProperty<? super Clone>[] properties;

        public CloneOrdering(List<? extends VDJCSProperty<? super Clone>> properties) {
            this.properties = properties.toArray(new VDJCSProperty[properties.size()]);
        }

        @JsonCreator
        public CloneOrdering(@JsonProperty("properties") VDJCSProperty<? super Clone>... properties) {
            this.properties = properties;
        }

        public List<VDJCSProperty<? super Clone>> getProperties() {
            return Arrays.asList(properties);
        }

        public Comparator<Clone> comparator() {
            return SortingUtil.combine(properties);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CloneOrdering that = (CloneOrdering) o;
            return Arrays.equals(properties, that.properties);
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(properties);
        }

        @Override
        public String toString() {
            try {
                return GlobalObjectMappers.getPretty().writeValueAsString(this);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @JsonAutoDetect(
            fieldVisibility = JsonAutoDetect.Visibility.ANY,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE,
            getterVisibility = JsonAutoDetect.Visibility.NONE
    )
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = NSequence.class, name = "nSequence"),
            @JsonSubTypes.Type(value = AASequence.class, name = "aaSequence"),
            @JsonSubTypes.Type(value = VDJCSegment.class, name = "segment"),
            @JsonSubTypes.Type(value = CloneCount.class, name = "cloneCount")
    })
    @Serializable(asJson = true)
    public interface VDJCSProperty<T extends VDJCObject> extends SortingProperty<T> {
    }

    public static final class NSequence extends AbstractHashSortingProperty.Natural<VDJCObject, NucleotideSequence> implements VDJCSProperty<VDJCObject> {
        @JsonProperty("geneFeature")
        public final GeneFeature geneFeature;

        @JsonCreator
        public NSequence(@JsonProperty("geneFeature") GeneFeature geneFeature) {
            Objects.requireNonNull(geneFeature);
            this.geneFeature = geneFeature;
        }

        @Override
        public SortingPropertyRelation relationTo(SortingProperty<?> other) {
            if (equals(other))
                return SortingPropertyRelation.Equal;

            // The following relations calculated with assumption of equal partitioning of
            // this and other sequences. Chance of different partitions considered negligible.

            if (other instanceof NSequence) {
                GeneFeature otherGeneFeature = ((NSequence) other).geneFeature;
                if (otherGeneFeature.contains(geneFeature))
                    return Necessary;
                if (geneFeature.contains(otherGeneFeature))
                    return Sufficient;
            }

            if (other instanceof AASequence) {
                GeneFeature aaGeneFeature = ((AASequence) other).geneFeature;
                if (geneFeature.contains(aaGeneFeature))
                    return Sufficient;
            }

            return None;
        }

        @Override
        public NucleotideSequence get(VDJCObject obj) {
            return obj.getNFeature(geneFeature);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof NSequence)) return false;
            NSequence nSequence = (NSequence) o;
            return geneFeature.equals(nSequence.geneFeature);
        }

        @Override
        public int hashCode() {
            return Objects.hash(geneFeature);
        }
    }

    public static final class AASequence extends AbstractHashSortingProperty.Natural<VDJCObject, AminoAcidSequence> implements VDJCSProperty<VDJCObject> {
        @JsonProperty("geneFeature")
        public final GeneFeature geneFeature;

        @JsonCreator
        public AASequence(@JsonProperty("geneFeature") GeneFeature geneFeature) {
            Objects.requireNonNull(geneFeature);
            this.geneFeature = geneFeature;
        }

        @Override
        public SortingPropertyRelation relationTo(SortingProperty<?> other) {
            if (equals(other))
                return SortingPropertyRelation.Equal;

            // The following relations calculated with assumption of equal partitioning of
            // this and other sequences. Chance of different partitions considered negligible.

            if (other instanceof AASequence) {
                GeneFeature otherGeneFeature = ((AASequence) other).geneFeature;
                if (otherGeneFeature.contains(geneFeature))
                    return Necessary;
                if (geneFeature.contains(otherGeneFeature))
                    return Sufficient;
            }

            if (other instanceof NSequence) {
                GeneFeature nGeneFeature = ((NSequence) other).geneFeature;
                if (nGeneFeature.contains(geneFeature))
                    return Necessary;
            }

            return None;
        }

        @Override
        public AminoAcidSequence get(VDJCObject obj) {
            return obj.getAAFeature(geneFeature);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof AASequence)) return false;
            AASequence that = (AASequence) o;
            return geneFeature.equals(that.geneFeature);
        }

        @Override
        public int hashCode() {
            return Objects.hash(geneFeature);
        }
    }

    public static final class VDJCSegment extends AbstractHashSortingProperty.Natural<VDJCObject, VDJCGeneId> implements VDJCSProperty<VDJCObject> {
        @JsonProperty("geneType")
        public final GeneType geneType;

        @JsonCreator
        public VDJCSegment(@JsonProperty("geneType") GeneType geneType) {
            this.geneType = geneType;
        }

        @Override
        public VDJCGeneId get(VDJCObject obj) {
            return obj.getBestHitGene(geneType).getId();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VDJCSegment that = (VDJCSegment) o;
            return geneType == that.geneType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(geneType);
        }
    }

    public static final class CloneCount extends AbstractSortingProperty<Clone, Double> implements VDJCSProperty<Clone> {
        @Override
        public Double get(Clone obj) {
            return obj.getCount();
        }

        @Override
        public Comparator<Double> propertyComparator() {
            return Comparator.<Double>naturalOrder().reversed();
        }

        @Override
        public int hashCode() {
            return CloneCount.class.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            return obj != null && obj.getClass() == CloneCount.class;
        }
    }
}
