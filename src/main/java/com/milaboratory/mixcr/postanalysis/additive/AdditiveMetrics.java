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
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCObject;
import com.milaboratory.mixcr.postanalysis.additive.AAProperties.AAProperty;
import com.milaboratory.mixcr.postanalysis.additive.AAProperties.Adjustment;
import io.repseq.core.GeneFeature;

import java.util.Objects;

/**
 *
 */
public final class AdditiveMetrics {
    private AdditiveMetrics() {}


    @JsonAutoDetect(
            fieldVisibility = JsonAutoDetect.Visibility.NON_PRIVATE,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE,
            getterVisibility = JsonAutoDetect.Visibility.NONE)
    interface JsonSupport {}

    public static final class Constant implements AdditiveMetric<Clone>, JsonSupport {
        public double value = 1.0;

        public Constant() {}

        public Constant(double value) {
            this.value = value;
        }

        @Override
        public double compute(Clone obj) {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Constant constant = (Constant) o;
            return Double.compare(constant.value, value) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(value);
        }
    }

    public static final class GeneFeatureLength<T extends VDJCObject> implements AdditiveMetric<T>, JsonSupport {
        public GeneFeature geneFeature;
        public boolean aminoAcid;

        public GeneFeatureLength() {}

        public GeneFeatureLength(GeneFeature geneFeature, boolean aminoAcid) {
            this.geneFeature = geneFeature;
            this.aminoAcid = aminoAcid;
        }

        @Override
        public double compute(T obj) {
            int len = aminoAcid ? obj.aaLengthOf(geneFeature) : obj.ntLengthOf(geneFeature);
            if (len < 0)
                return Double.NaN;
            return len;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GeneFeatureLength<?> that = (GeneFeatureLength<?>) o;
            return aminoAcid == that.aminoAcid &&
                    Objects.equals(geneFeature, that.geneFeature);
        }

        @Override
        public String toString() {
            return "Length of " + geneFeature + ", " + (aminoAcid ? "aa" : "nt");
        }

        @Override
        public int hashCode() {
            return Objects.hash(geneFeature, aminoAcid);
        }
    }

    public static final class AddedNucleotides<T extends VDJCObject> implements AdditiveMetric<T>, JsonSupport {
        public AddedNucleotides() {}

        @Override
        public double compute(T obj) {
            int vd = obj.ntLengthOf(GeneFeature.VDJunction);
            int dj = obj.ntLengthOf(GeneFeature.DJJunction);
            if (vd >= 0 && dj >= 0)
                return vd + dj;
            if (obj.getFeature(GeneFeature.DCDR3Part) != null)
                return Double.NaN; // no CDR3
            int vj = obj.ntLengthOf(GeneFeature.VJJunction);
            if (vj < 0)
                return Double.NaN;
            return vj;
        }

        @Override
        public String toString() {
            return "Added nucleotides";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            return true;
        }

        @Override
        public int hashCode() {
            return 7891;
        }
    }

    public static final class AAPropertyNormalized implements AdditiveMetric<Clone>, JsonSupport {
        public AAProperty property;
        public GeneFeature geneFeature = GeneFeature.CDR3;

        public AAPropertyNormalized() {}

        public AAPropertyNormalized(AAProperty property, GeneFeature geneFeature) {
            this.property = property;
            this.geneFeature = geneFeature;
        }

        @Override
        public double compute(Clone obj) {
            return AAProperties.computeNormalized(property, obj, geneFeature);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AAPropertyNormalized that = (AAPropertyNormalized) o;
            return property == that.property &&
                    Objects.equals(geneFeature, that.geneFeature);
        }

        @Override
        public String toString() {
            return property + " of " + geneFeature;
        }

        @Override
        public int hashCode() {
            return Objects.hash(property, geneFeature);
        }
    }

    public static final class AAPropertySum implements AdditiveMetric<Clone>, JsonSupport {
        public AAProperty property;
        public GeneFeature geneFeature = GeneFeature.CDR3;
        public Adjustment adjustment = Adjustment.LeadingCenter;
        public int nLetters = 5;

        public AAPropertySum() {}

        public AAPropertySum(AAProperty property, GeneFeature geneFeature, Adjustment adjustment, int nLetters) {
            this.property = property;
            this.geneFeature = geneFeature;
            this.adjustment = adjustment;
            this.nLetters = nLetters;
        }

        @Override
        public double compute(Clone obj) {
            return AAProperties.compute(property, obj, geneFeature, adjustment, nLetters);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AAPropertySum that = (AAPropertySum) o;
            return nLetters == that.nLetters &&
                    property == that.property &&
                    Objects.equals(geneFeature, that.geneFeature) &&
                    adjustment == that.adjustment;
        }

        @Override
        public int hashCode() {
            return Objects.hash(property, geneFeature, adjustment, nLetters);
        }
    }
}
