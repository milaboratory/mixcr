/*
 * Copyright (c) 2014-2015, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
 * (here and after addressed as Inventors)
 * All Rights Reserved
 *
 * Permission to use, copy, modify and distribute any part of this program for
 * educational, research and non-profit purposes, by non-profit institutions
 * only, without fee, and without a written agreement is hereby granted,
 * provided that the above copyright notice, this paragraph and the following
 * three paragraphs appear in all copies.
 *
 * Those desiring to incorporate this work into commercial products or use for
 * commercial purposes should contact the Inventors using one of the following
 * email addresses: chudakovdm@mail.ru, chudakovdm@gmail.com
 *
 * IN NO EVENT SHALL THE INVENTORS BE LIABLE TO ANY PARTY FOR DIRECT, INDIRECT,
 * SPECIAL, INCIDENTAL, OR CONSEQUENTIAL DAMAGES, INCLUDING LOST PROFITS,
 * ARISING OUT OF THE USE OF THIS SOFTWARE, EVEN IF THE INVENTORS HAS BEEN
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * THE SOFTWARE PROVIDED HEREIN IS ON AN "AS IS" BASIS, AND THE INVENTORS HAS
 * NO OBLIGATION TO PROVIDE MAINTENANCE, SUPPORT, UPDATES, ENHANCEMENTS, OR
 * MODIFICATIONS. THE INVENTORS MAKES NO REPRESENTATIONS AND EXTENDS NO
 * WARRANTIES OF ANY KIND, EITHER IMPLIED OR EXPRESS, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY OR FITNESS FOR A
 * PARTICULAR PURPOSE, OR THAT THE USE OF THE SOFTWARE WILL NOT INFRINGE ANY
 * PATENT, TRADEMARK OR OTHER RIGHTS.
 */
package com.milaboratory.mixcr.assembler;


import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.milaboratory.mixcr.basictypes.ClonalUpdatableParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import io.repseq.core.GeneType;

import java.util.EnumMap;
import java.util.Map;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
public final class CloneFactoryParameters implements java.io.Serializable {
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
    @JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
    public VJCClonalAlignerParameters getVParameters() {
        return vdcParameters.get(GeneType.Variable);
    }

    @JsonProperty("jParameters")
    @JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
    public VJCClonalAlignerParameters getJParameters() {
        return vdcParameters.get(GeneType.Joining);
    }

    @JsonProperty("cParameters")
    @JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
    public VJCClonalAlignerParameters getCParameters() {
        return vdcParameters.get(GeneType.Constant);
    }

    @JsonProperty("dParameters")
    @JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
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
