package com.milaboratory.mixcr.postanalysis.additive;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.milaboratory.core.sequence.AminoAcidSequence;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.TranslationParameters;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.basictypes.VDJCObject;
import com.milaboratory.mixcr.util.Tuple2;
import io.repseq.core.Chains;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;

import java.util.Objects;
import java.util.function.Function;

/**
 *
 */
public final class KeyFunctions {
    private KeyFunctions() {}

    @JsonAutoDetect(
            fieldVisibility = JsonAutoDetect.Visibility.NON_PRIVATE,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE,
            getterVisibility = JsonAutoDetect.Visibility.NONE)
    interface JsonSupport {}

    public static final class Named<T> implements KeyFunction<String, T>, JsonSupport {
        public String name;

        public Named() {}

        public Named(String name) {
            this.name = name;
        }

        @Override
        public String getKey(T obj) {
            return name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Named<?> named = (Named<?>) o;
            return Objects.equals(name, named.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name);
        }
    }

    abstract static class SegmentKeyFunction<K, T extends VDJCObject> implements KeyFunction<K, T> {
        @JsonIgnore
        final Function<VDJCGene, K> mapper;
        public GeneType geneType;

        public SegmentKeyFunction(Function<VDJCGene, K> mapper, GeneType geneType) {
            this.mapper = mapper;
            this.geneType = geneType;
        }

        public SegmentKeyFunction(Function<VDJCGene, K> mapper) {
            this.mapper = mapper;
        }

        @Override
        public K getKey(T obj) {
            VDJCHit hit = obj.getBestHit(geneType);
            if (hit == null)
                return null;
            return mapper.apply(hit.getGene());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SegmentKeyFunction<?, ?> that = (SegmentKeyFunction<?, ?>) o;
            return geneType == that.geneType;
        }

        @Override
        public int hashCode() {
            return Objects.hash(geneType);
        }
    }

    public static class SegmentUsage<T extends VDJCObject>
            extends SegmentKeyFunction<String, T>
            implements JsonSupport {
        @JsonCreator
        public SegmentUsage() {
            super(VDJCGene::getName);
        }

        public SegmentUsage(GeneType geneType) {
            super(VDJCGene::getName, geneType);
        }
    }

    public static class GeneUsage<T extends VDJCObject>
            extends SegmentKeyFunction<String, T>
            implements JsonSupport {
        @JsonCreator
        public GeneUsage() {
            super(VDJCGene::getGeneName);
        }

        public GeneUsage(GeneType geneType) {
            super(VDJCGene::getGeneName, geneType);
        }
    }

    public static class FamiltyUsage<T extends VDJCObject>
            extends SegmentKeyFunction<String, T>
            implements JsonSupport {
        @JsonCreator
        public FamiltyUsage() {
            super(VDJCGene::getFamilyName);
        }

        public FamiltyUsage(GeneType geneType) {
            super(VDJCGene::getFamilyName, geneType);
        }
    }

    public static final class VJGenes<Gene> implements JsonSupport {
        public Gene vGene;
        public Gene jGene;

        public VJGenes() {}

        public VJGenes(Gene vGene, Gene jGene) {
            this.vGene = vGene;
            this.jGene = jGene;
        }

        public <K> VJGenes<K> map(Function<Gene, K> mapper) {
            return new VJGenes<>(mapper.apply(vGene), mapper.apply(jGene));
        }

        @Override
        public String toString() {
            return vGene + "+" + jGene;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VJGenes<?> vjGenes = (VJGenes<?>) o;
            return Objects.equals(vGene, vjGenes.vGene) &&
                    Objects.equals(jGene, vjGenes.jGene);
        }

        @Override
        public int hashCode() {
            return Objects.hash(vGene, jGene);
        }
    }

    abstract static class VJKeyFunction<K, T extends VDJCObject> implements KeyFunction<VJGenes<K>, T> {
        @JsonIgnore
        final Function<VDJCGene, K> mapper;

        VJKeyFunction(Function<VDJCGene, K> mapper) {
            this.mapper = mapper;
        }

        @Override
        public VJGenes<K> getKey(T obj) {
            VDJCHit v = obj.getBestHit(GeneType.Variable);
            VDJCHit j = obj.getBestHit(GeneType.Joining);
            if (v == null || j == null)
                return null;
            return new VJGenes<>(v.getGene(), j.getGene()).map(mapper);
        }

        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return 17;
        }
    }

    public static class VJSegmentUsage<T extends VDJCObject>
            extends VJKeyFunction<String, T>
            implements JsonSupport {
        public VJSegmentUsage() {
            super(VDJCGene::getName);
        }
    }

    public static class VJGeneUsage<T extends VDJCObject>
            extends VJKeyFunction<String, T>
            implements JsonSupport {
        public VJGeneUsage() {
            super(VDJCGene::getGeneName);
        }
    }

    public static class VJFamilyUsage<T extends VDJCObject>
            extends VJKeyFunction<String, T>
            implements JsonSupport {
        public VJFamilyUsage() {
            super(VDJCGene::getFamilyName);
        }
    }

    public enum Isotype {
        A("IgA"), D("IgD"), G("IgG"), E("IgE"), M("IgM");
        private final String name;

        Isotype(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }

        static Isotype valueOf(char c) {
            switch (c) {
                case 'a': case 'A': return A;
                case 'd': case 'D': return D;
                case 'g': case 'G': return G;
                case 'e': case 'E': return E;
                case 'm': case 'M': return M;
                default: return null;
            }
        }
    }

    public static class IsotypeUsage<T extends VDJCObject> implements KeyFunction<Isotype, T>, JsonSupport {
        @Override
        public Isotype getKey(T obj) {
            VDJCHit cHit = obj.getBestHit(GeneType.Constant);
            if (cHit == null)
                return null;
            if (!cHit.getGene().getChains().intersects(Chains.IGH))
                return null;
            String gene = cHit.getGene().getName();
            if (gene.length() < 4)
                return null;
            return Isotype.valueOf(gene.charAt(3));
        }

        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return 17;
        }
    }

    public static final class NTFeature implements KeyFunction<String, Clone>, JsonSupport {
        public GeneFeature geneFeature;

        public NTFeature(GeneFeature geneFeature) {
            this.geneFeature = geneFeature;
        }

        public NTFeature() {}

        @Override
        public String getKey(Clone obj) {
            NSequenceWithQuality f = obj.getFeature(geneFeature);
            if (f == null)
                return null;
            return f.getSequence().toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            NTFeature ntFeature = (NTFeature) o;
            return Objects.equals(geneFeature, ntFeature.geneFeature);
        }

        @Override
        public int hashCode() {
            return Objects.hash(geneFeature);
        }
    }

    public static final class AAFeature implements KeyFunction<String, Clone>, JsonSupport {
        public GeneFeature geneFeature;

        public AAFeature(GeneFeature geneFeature) {
            this.geneFeature = geneFeature;
        }

        public AAFeature() {}

        @Override
        public String getKey(Clone obj) {
            NSequenceWithQuality feature = obj.getFeature(geneFeature);
            if (feature == null)
                return null;
            int targetId = obj.getTargetContainingFeature(geneFeature);
            TranslationParameters tr = targetId == -1 ?
                    TranslationParameters.FromLeftWithIncompleteCodon
                    : obj.getPartitionedTarget(targetId).getPartitioning().getTranslationParameters(geneFeature);
            if (tr == null)
                return null;
            return AminoAcidSequence.translate(feature.getSequence(), tr).toString();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AAFeature aaFeature = (AAFeature) o;
            return Objects.equals(geneFeature, aaFeature.geneFeature);
        }

        @Override
        public int hashCode() {
            return Objects.hash(geneFeature);
        }
    }

    public static final class Tuple2Key<K1, K2, T> implements KeyFunction<Tuple2<K1, K2>, T>, JsonSupport {
        public KeyFunction<K1, T> key1;
        public KeyFunction<K2, T> key2;

        @Override
        public Tuple2<K1, K2> getKey(T obj) {
            K1 k1 = key1.getKey(obj);
            K2 k2 = key2.getKey(obj);
            if (k1 == null && k2 == null)
                return null;
            return new Tuple2<>(k1, k2);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Tuple2Key<?, ?, ?> tuple2Key = (Tuple2Key<?, ?, ?>) o;
            return Objects.equals(key1, tuple2Key.key1) &&
                    Objects.equals(key2, tuple2Key.key2);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key1, key2);
        }
    }
}
