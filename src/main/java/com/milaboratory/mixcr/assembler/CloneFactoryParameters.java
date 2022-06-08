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
package com.milaboratory.mixcr.assembler;


import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.milaboratory.mixcr.basictypes.ClonalUpdatableParameters;
import com.milaboratory.mixcr.basictypes.HasRelativeMinScore;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import io.repseq.core.GeneType;

import java.util.EnumMap;
import java.util.Map;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
public final class CloneFactoryParameters implements HasRelativeMinScore, java.io.Serializable {
    EnumMap<GeneType, VJCClonalAlignerParameters> vdcParameters = new EnumMap<>(GeneType.class);
    @JsonSerialize(as = DClonalAlignerParameters.class)
    DClonalAlignerParameters dParameters;

    @JsonCreator
    public CloneFactoryParameters(@JsonProperty("vParameters") VJCClonalAlignerParameters vParameters,
                                  @JsonProperty("jParameters") VJCClonalAlignerParameters jParameters,
                                  @JsonProperty("cParameters") VJCClonalAlignerParameters cParameters,
                                  @JsonProperty("dParameters") DClonalAlignerParameters dParameters) {
        if (vParameters != null)
            vdcParameters.put(GeneType.Variable, vParameters);
        if (jParameters != null)
            vdcParameters.put(GeneType.Joining, jParameters);
        if (cParameters != null)
            vdcParameters.put(GeneType.Constant, cParameters);
        this.dParameters = dParameters;
    }

    CloneFactoryParameters(EnumMap<GeneType, VJCClonalAlignerParameters> vdcParameters, DClonalAlignerParameters dParameters) {
        this.vdcParameters = vdcParameters;
        this.dParameters = dParameters;
    }

    private ClonalUpdatableParameters uParameters(GeneType gt) {
        return gt == GeneType.Diversity ? dParameters : vdcParameters.get(gt);
    }

    public void update(VDJCAlignerParameters alignerParameters) {
        for (GeneType gt : GeneType.VDJC_REFERENCE) {
            ClonalUpdatableParameters up = uParameters(gt);
            if (up != null)
                up.updateFrom(alignerParameters.getGeneAlignerParameters(gt));
        }
    }

    public boolean isComplete() {
        for (GeneType gt : GeneType.VDJC_REFERENCE) {
            ClonalUpdatableParameters up = uParameters(gt);
            if (up != null && !up.isComplete())
                return false;
        }
        return true;
    }

    public VJCClonalAlignerParameters getVJCParameters(GeneType geneType) {
        return vdcParameters.get(geneType);
    }

    @JsonProperty("vParameters")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public VJCClonalAlignerParameters getVParameters() {
        return vdcParameters.get(GeneType.Variable);
    }

    @JsonProperty("jParameters")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public VJCClonalAlignerParameters getJParameters() {
        return vdcParameters.get(GeneType.Joining);
    }

    @JsonProperty("cParameters")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public VJCClonalAlignerParameters getCParameters() {
        return vdcParameters.get(GeneType.Constant);
    }

    @JsonProperty("dParameters")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public DClonalAlignerParameters getDParameters() {
        return dParameters;
    }

    public CloneFactoryParameters setVJCParameters(GeneType geneType, VJCClonalAlignerParameters parameters) {
        if (parameters == null)
            vdcParameters.remove(geneType);
        else
            vdcParameters.put(geneType, parameters);
        return this;
    }

    public CloneFactoryParameters setVParameters(VJCClonalAlignerParameters parameters) {
        setVJCParameters(GeneType.Variable, parameters);
        return this;
    }

    public CloneFactoryParameters setJParameters(VJCClonalAlignerParameters parameters) {
        setVJCParameters(GeneType.Joining, parameters);
        return this;
    }

    public CloneFactoryParameters setCParameters(VJCClonalAlignerParameters parameters) {
        setVJCParameters(GeneType.Constant, parameters);
        return this;
    }

    public CloneFactoryParameters setDParameters(DClonalAlignerParameters dParameters) {
        this.dParameters = dParameters;
        return this;
    }

    @Override
    public float getRelativeMinScore(GeneType gt) {
        if (gt == GeneType.Diversity)
            return getDParameters().getRelativeMinScore();
        VJCClonalAlignerParameters geneSpecificParameters = getVJCParameters(gt);
        return geneSpecificParameters == null ? Float.NaN : geneSpecificParameters.getRelativeMinScore();
    }

    @Override
    protected CloneFactoryParameters clone() {
        EnumMap<GeneType, VJCClonalAlignerParameters> vjc = new EnumMap<>(GeneType.class);
        for (Map.Entry<GeneType, VJCClonalAlignerParameters> entry : vdcParameters.entrySet())
            vjc.put(entry.getKey(), entry.getValue().clone());
        return new CloneFactoryParameters(vjc, dParameters.clone());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CloneFactoryParameters)) return false;

        CloneFactoryParameters that = (CloneFactoryParameters) o;

        if (dParameters != null ? !dParameters.equals(that.dParameters) : that.dParameters != null) return false;
        if (!vdcParameters.equals(that.vdcParameters)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = vdcParameters.hashCode();
        result = 31 * result + (dParameters != null ? dParameters.hashCode() : 0);
        return result;
    }
}
