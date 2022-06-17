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
                NormalizedShannonWeinerIndex,
                GiniIndex,
                InverseSimpson,
                EfronThisted,
                EfronThistedStd,
        };
    }

    public static final DiversityMeasure Observed = Measure.Observed.toDiversityMeasure();
    public static final DiversityMeasure Chao1 = Measure.Chao1.toDiversityMeasure();
    public static final DiversityMeasure Chao1Std = Measure.Chao1Std.toDiversityMeasure();
    public static final DiversityMeasure ShannonWiener = Measure.ShannonWiener.toDiversityMeasure();
    public static final DiversityMeasure NormalizedShannonWeinerIndex = Measure.NormalizedShannonWeinerIndex.toDiversityMeasure();
    public static final DiversityMeasure GiniIndex = Measure.Gini.toDiversityMeasure();
    public static final DiversityMeasure InverseSimpson = Measure.InverseSimpson.toDiversityMeasure();
    public static final DiversityMeasure EfronThisted = Measure.EfronThisted.toDiversityMeasure();
    public static final DiversityMeasure EfronThistedStd = Measure.EfronThistedStd.toDiversityMeasure();

    public enum Measure {
        Observed,
        Chao1,
        Chao1Std,
        ShannonWiener,
        NormalizedShannonWeinerIndex,
        Gini,
        GiniDiversity,
        InverseSimpson,
        EfronThisted,
        EfronThistedStd;

        public DiversityMeasure toDiversityMeasure() {
            return new DiversityMeasure(this, this.name());
        }
    }
}
