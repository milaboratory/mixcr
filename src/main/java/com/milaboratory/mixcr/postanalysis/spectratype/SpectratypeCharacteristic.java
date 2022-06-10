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
package com.milaboratory.mixcr.postanalysis.spectratype;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.postanalysis.*;

import java.util.Objects;

/**
 *
 */
public class SpectratypeCharacteristic extends Characteristic<SpectratypeKey<String>, Clone> {
    @JsonProperty("nTopClonotypes")
    public final int nTopClonotypes;
    @JsonProperty("keyFunction")
    public final SpectratypeKeyFunction<String, Clone> keyFunction;

    @JsonCreator
    public SpectratypeCharacteristic(@JsonProperty("name") String name,
                                     @JsonProperty("preprocessor") SetPreprocessorFactory<Clone> preprocessor,
                                     @JsonProperty("weight") WeightFunction<Clone> weight,
                                     @JsonProperty("nTopClonotypes") int nTopClonotypes,
                                     @JsonProperty("keyFunction") SpectratypeKeyFunction<String, Clone> keyFunction) {
        super(name, preprocessor, weight);
        this.nTopClonotypes = nTopClonotypes;
        this.keyFunction = keyFunction;
    }

    public SpectratypeCharacteristic(String name,
                                     SetPreprocessorFactory<Clone> preprocessor,
                                     int nTopClonotypes,
                                     SpectratypeKeyFunction<String, Clone> keyFunction) {
        this(name, preprocessor, WeightFunctions.Count, nTopClonotypes, keyFunction);
    }

    @Override
    protected Aggregator<SpectratypeKey<String>, Clone> createAggregator(Dataset<Clone> dataset) {
        return new SpectratypeAggregator<>(nTopClonotypes, keyFunction, weight);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false;
        SpectratypeCharacteristic that = (SpectratypeCharacteristic) o;
        return nTopClonotypes == that.nTopClonotypes &&
                Objects.equals(keyFunction, that.keyFunction);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), nTopClonotypes, keyFunction);
    }
}
