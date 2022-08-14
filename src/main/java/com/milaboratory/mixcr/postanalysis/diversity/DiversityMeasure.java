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
package com.milaboratory.mixcr.postanalysis.diversity;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 *
 */
public final class DiversityMeasure {
    @JsonProperty("measure")
    public final Measure measure;
    @JsonProperty("name")
    public final String name;

    @JsonCreator
    public DiversityMeasure(@JsonProperty("measure") Measure measure,
                            @JsonProperty("name") String name) {
        this.measure = measure;
        this.name = name;
    }

    public DiversityMeasure overrideName(String newName) {
        return new DiversityMeasure(measure, newName);
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DiversityMeasure that = (DiversityMeasure) o;
        return measure == that.measure && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(measure, name);
    }

    public static DiversityMeasure[] basic() {
        return new DiversityMeasure[]{
                Observed,
                Chao1,
                Chao1Std,
                ShannonWiener,
                NormalizedShannonWienerIndex,
                GiniIndex,
                InverseSimpsonIndex,
                EfronThisted,
                EfronThistedStd,
        };
    }

    public static final DiversityMeasure Observed = Measure.Observed.toDiversityMeasure();
    public static final DiversityMeasure Chao1 = Measure.Chao1.toDiversityMeasure();
    public static final DiversityMeasure Chao1Std = Measure.Chao1Std.toDiversityMeasure();
    public static final DiversityMeasure ShannonWiener = Measure.ShannonWiener.toDiversityMeasure();
    public static final DiversityMeasure NormalizedShannonWienerIndex = Measure.NormalizedShannonWienerIndex.toDiversityMeasure();
    public static final DiversityMeasure GiniIndex = Measure.GiniIndex.toDiversityMeasure();
    public static final DiversityMeasure InverseSimpsonIndex = Measure.InverseSimpsonIndex.toDiversityMeasure();
    public static final DiversityMeasure EfronThisted = Measure.EfronThisted.toDiversityMeasure();
    public static final DiversityMeasure EfronThistedStd = Measure.EfronThistedStd.toDiversityMeasure();

    public enum Measure {
        Observed("Observed diversity"),
        NormalizedShannonWienerIndex("Normalized Shannon-Wiener index"),
        ShannonWiener("Shannon-Wiener diversity"),
        Chao1("Chao1 estimate"),
        Chao1Std("Chao1 estimate std dev"),
        GiniIndex("Gini index"),
        InverseSimpsonIndex("Inverse Simpson index"),
        EfronThisted("Efron-Thisted estimate"),
        EfronThistedStd("Efron-Thisted estimate std dev");

        private final String readableName;

        Measure(String readableName) {
            this.readableName = readableName;
        }

        public DiversityMeasure toDiversityMeasure() {
            return new DiversityMeasure(this, readableName);
        }
    }
}
