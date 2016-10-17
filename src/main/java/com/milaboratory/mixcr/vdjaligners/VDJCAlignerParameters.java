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
package com.milaboratory.mixcr.vdjaligners;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.milaboratory.core.PairedEndReadsLayout;
import com.milaboratory.core.merger.MergerParameters;
import com.milaboratory.mixcr.basictypes.HasFeatureToAlign;
import com.milaboratory.primitivio.annotations.Serializable;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;

import java.util.EnumMap;
import java.util.Map;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
@Serializable(asJson = true)
public final class VDJCAlignerParameters implements HasFeatureToAlign, java.io.Serializable {
    @JsonIgnore
    protected final EnumMap<GeneType, GeneAlignmentParameters> alignmentParameters;
    protected VJAlignmentOrder vjAlignmentOrder;
    protected boolean includeDScore, includeCScore;
    protected float minSumScore;
    protected int maxHits;
    protected float relativeMinVFR3CDR3Score;
    protected boolean allowPartialAlignments, allowNoCDR3PartAlignments, allowChimeras;
    protected PairedEndReadsLayout readsLayout;
    protected MergerParameters mergerParameters;
    protected boolean fixSeed;
    protected int vjOverlapWindow;

    @JsonCreator
    public VDJCAlignerParameters(@JsonProperty("vParameters") KGeneAlignmentParameters vParameters,
                                 @JsonProperty("dParameters") DAlignerParameters dParameters,
                                 @JsonProperty("jParameters") KGeneAlignmentParameters jParameters,
                                 @JsonProperty("cParameters") KGeneAlignmentParameters cParameters,
                                 @JsonProperty("vjAlignmentOrder") VJAlignmentOrder vjAlignmentOrder,
                                 @JsonProperty("includeDScore") boolean includeDScore,
                                 @JsonProperty("includeCScore") boolean includeCScore,
                                 @JsonProperty("minSumScore") float minSumScore,
                                 @JsonProperty("maxHits") int maxHits,
                                 @JsonProperty("relativeMinVFR3CDR3Score") float relativeMinVFR3CDR3Score,
                                 @JsonProperty("allowPartialAlignments") boolean allowPartialAlignments,
                                 @JsonProperty("allowNoCDR3PartAlignments") boolean allowNoCDR3PartAlignments,
                                 @JsonProperty("allowChimeras") boolean allowChimeras,
                                 @JsonProperty("readsLayout") PairedEndReadsLayout readsLayout,
                                 @JsonProperty("mergerParameters") MergerParameters mergerParameters,
                                 @JsonProperty("fixSeed") boolean fixSeed,
                                 @JsonProperty("vjOverlapWindow") int vjOverlapWindow) {
        this.alignmentParameters = new EnumMap<>(GeneType.class);
        setGeneAlignerParameters(GeneType.Variable, vParameters);
        setGeneAlignerParameters(GeneType.Diversity, dParameters);
        setGeneAlignerParameters(GeneType.Joining, jParameters);
        setGeneAlignerParameters(GeneType.Constant, cParameters);
        this.vjAlignmentOrder = vjAlignmentOrder;
        this.includeDScore = includeDScore;
        this.includeCScore = includeCScore;
        this.minSumScore = minSumScore;
        this.maxHits = maxHits;
        this.relativeMinVFR3CDR3Score = relativeMinVFR3CDR3Score;
        this.allowPartialAlignments = allowPartialAlignments;
        this.allowNoCDR3PartAlignments = allowNoCDR3PartAlignments;
        this.allowChimeras = allowChimeras;
        this.readsLayout = readsLayout;
        this.mergerParameters = mergerParameters;
        this.fixSeed = fixSeed;
        this.vjOverlapWindow = vjOverlapWindow;
    }

    public VDJCAlignerParameters setVjOverlapWindow(int vjOverlapWindow) {
        this.vjOverlapWindow = vjOverlapWindow;
        return this;
    }

    public VJAlignmentOrder getVjAlignmentOrder() {
        return vjAlignmentOrder;
    }

    public VDJCAlignerParameters setFixSeed(boolean fixSeed) {
        this.fixSeed = fixSeed;
        return this;
    }

    public boolean isFixSeed() {
        return fixSeed;
    }

    public VDJCAlignerParameters setGeneAlignerParameters(GeneType gt, GeneAlignmentParameters parameters) {
        if (parameters != null && parameters.getGeneType() != gt)
            throw new IllegalArgumentException();
        if (parameters == null)
            alignmentParameters.remove(gt);
        else
            alignmentParameters.put(gt, parameters);
        return this;
    }

    public VDJCAlignerParameters setVAlignmentParameters(KGeneAlignmentParameters parameters) {
        setGeneAlignerParameters(GeneType.Variable, parameters);
        return this;
    }

    public VDJCAlignerParameters setDAlignmentParameters(DAlignerParameters parameters) {
        setGeneAlignerParameters(GeneType.Diversity, parameters);
        return this;
    }

    public VDJCAlignerParameters setJAlignmentParameters(KGeneAlignmentParameters parameters) {
        setGeneAlignerParameters(GeneType.Joining, parameters);
        return this;
    }

    public VDJCAlignerParameters setCAlignmentParameters(KGeneAlignmentParameters parameters) {
        setGeneAlignerParameters(GeneType.Constant, parameters);
        return this;
    }

    public boolean getAllowPartialAlignments() {
        return allowPartialAlignments;
    }

    public VDJCAlignerParameters setAllowPartialAlignments(boolean allowPartialAlignments) {
        this.allowPartialAlignments = allowPartialAlignments;
        return this;
    }

    public boolean isAllowChimeras() {
        return allowChimeras;
    }

    public VDJCAlignerParameters setAllowChimeras(boolean allowChimeras) {
        this.allowChimeras = allowChimeras;
        return this;
    }

    public boolean getAllowNoCDR3PartAlignments() {
        return allowNoCDR3PartAlignments;
    }

    public VDJCAlignerParameters setAllowNoCDR3PartAlignments(boolean allowNoCDR3PartAlignments) {
        this.allowNoCDR3PartAlignments = allowNoCDR3PartAlignments;
        return this;
    }

    public GeneAlignmentParameters getGeneAlignerParameters(GeneType geneType) {
        return alignmentParameters.get(geneType);
    }

    public KGeneAlignmentParameters getVJCGeneAlignerParameters(GeneType geneType) {
        return (KGeneAlignmentParameters) getGeneAlignerParameters(geneType);
    }

    @JsonProperty("vParameters")
    @JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
    public KGeneAlignmentParameters getVAlignerParameters() {
        return getVJCGeneAlignerParameters(GeneType.Variable);
    }

    @JsonProperty("dParameters")
    @JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
    public DAlignerParameters getDAlignerParameters() {
        return (DAlignerParameters) getGeneAlignerParameters(GeneType.Diversity);
    }

    @JsonProperty("jParameters")
    @JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
    public KGeneAlignmentParameters getJAlignerParameters() {
        return getVJCGeneAlignerParameters(GeneType.Joining);
    }

    @JsonProperty("cParameters")
    @JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
    public KGeneAlignmentParameters getCAlignerParameters() {
        return getVJCGeneAlignerParameters(GeneType.Constant);
    }

    public boolean containsRequiredFeature(VDJCGene gene) {
        GeneFeature featureToAlign = getFeatureToAlign(gene.getGeneType());
        return featureToAlign != null && gene.getPartitioning().isAvailable(featureToAlign);
    }

    @JsonProperty("minSumScore")
    public float getMinSumScore() {
        return minSumScore;
    }

    @JsonProperty("vjAlignmentOrder")
    public VJAlignmentOrder getVJAlignmentOrder() {
        return vjAlignmentOrder;
    }

    public void setVjAlignmentOrder(VJAlignmentOrder vjAlignmentOrder) {
        this.vjAlignmentOrder = vjAlignmentOrder;
    }

    public boolean doIncludeDScore() {
        return includeDScore;
    }

    public void setIncludeDScore(boolean includeDScore) {
        this.includeDScore = includeDScore;
    }

    public boolean doIncludeCScore() {
        return includeCScore;
    }

    public void setIncludeCScore(boolean includeCScore) {
        this.includeCScore = includeCScore;
    }

    public VDJCAlignerParameters setMinSumScore(float minSumScore) {
        this.minSumScore = minSumScore;
        return this;
    }

    @JsonProperty("maxHits")
    public int getMaxHits() {
        return maxHits;
    }

    public VDJCAlignerParameters setMaxHits(int maxHits) {
        this.maxHits = maxHits;
        return this;
    }

    @Override
    public GeneFeature getFeatureToAlign(GeneType type) {
        GeneAlignmentParameters params = alignmentParameters.get(type);
        return params == null ? null : params.getGeneFeatureToAlign();
    }

    protected EnumMap<GeneType, GeneAlignmentParameters> getCloneOfAlignmentParameters() {
        EnumMap<GeneType, GeneAlignmentParameters> map = new EnumMap<GeneType, GeneAlignmentParameters>(GeneType.class);
        for (Map.Entry<GeneType, GeneAlignmentParameters> entry : alignmentParameters.entrySet())
            map.put(entry.getKey(), entry.getValue().clone());
        return map;
    }

    public float getRelativeMinVFR3CDR3Score() {
        return relativeMinVFR3CDR3Score;
    }

    public VDJCAlignerParameters setRelativeMinVFR3CDR3Score(float relativeMinVFR3CDR3Score) {
        this.relativeMinVFR3CDR3Score = relativeMinVFR3CDR3Score;
        return this;
    }

    public PairedEndReadsLayout getReadsLayout() {
        return readsLayout;
    }

    public VDJCAlignerParameters setReadsLayout(PairedEndReadsLayout readsLayout) {
        this.readsLayout = readsLayout;
        return this;
    }

    public void setMergerParameters(MergerParameters mergerParameters) {
        this.mergerParameters = mergerParameters;
    }

    public MergerParameters getMergerParameters() {
        return mergerParameters;
    }

    @Override
    public String toString() {
        return "VDJCAlignerParameters{" +
                "alignmentParameters=" + alignmentParameters +
                ", vjAlignmentOrder=" + vjAlignmentOrder +
                ", includeDScore=" + includeDScore +
                ", includeCScore=" + includeCScore +
                ", minSumScore=" + minSumScore +
                ", maxHits=" + maxHits +
                ", relativeMinVFR3CDR3Score=" + relativeMinVFR3CDR3Score +
                ", allowPartialAlignments=" + allowPartialAlignments +
                ", allowNoCDR3PartAlignments=" + allowNoCDR3PartAlignments +
                ", allowChimeras=" + allowChimeras +
                ", readsLayout=" + readsLayout +
                ", mergerParameters=" + mergerParameters +
                ", fixSeed=" + fixSeed +
                ", vjOverlapWindow=" + vjOverlapWindow +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VDJCAlignerParameters that = (VDJCAlignerParameters) o;

        if (includeDScore != that.includeDScore) return false;
        if (includeCScore != that.includeCScore) return false;
        if (Float.compare(that.minSumScore, minSumScore) != 0) return false;
        if (maxHits != that.maxHits) return false;
        if (Float.compare(that.relativeMinVFR3CDR3Score, relativeMinVFR3CDR3Score) != 0) return false;
        if (allowPartialAlignments != that.allowPartialAlignments) return false;
        if (allowNoCDR3PartAlignments != that.allowNoCDR3PartAlignments) return false;
        if (allowChimeras != that.allowChimeras) return false;
        if (fixSeed != that.fixSeed) return false;
        if (vjOverlapWindow != that.vjOverlapWindow) return false;
        if (alignmentParameters != null ? !alignmentParameters.equals(that.alignmentParameters) : that.alignmentParameters != null)
            return false;
        if (vjAlignmentOrder != that.vjAlignmentOrder) return false;
        if (readsLayout != that.readsLayout) return false;
        return !(mergerParameters != null ? !mergerParameters.equals(that.mergerParameters) : that.mergerParameters != null);

    }

    @Override
    public int hashCode() {
        int result = alignmentParameters != null ? alignmentParameters.hashCode() : 0;
        result = 31 * result + (vjAlignmentOrder != null ? vjAlignmentOrder.hashCode() : 0);
        result = 31 * result + (includeDScore ? 1 : 0);
        result = 31 * result + (includeCScore ? 1 : 0);
        result = 31 * result + (minSumScore != +0.0f ? Float.floatToIntBits(minSumScore) : 0);
        result = 31 * result + maxHits;
        result = 31 * result + (relativeMinVFR3CDR3Score != +0.0f ? Float.floatToIntBits(relativeMinVFR3CDR3Score) : 0);
        result = 31 * result + (allowPartialAlignments ? 1 : 0);
        result = 31 * result + (allowNoCDR3PartAlignments ? 1 : 0);
        result = 31 * result + (allowChimeras ? 1 : 0);
        result = 31 * result + (readsLayout != null ? readsLayout.hashCode() : 0);
        result = 31 * result + (mergerParameters != null ? mergerParameters.hashCode() : 0);
        result = 31 * result + (fixSeed ? 1 : 0);
        result = 31 * result + vjOverlapWindow;
        return result;
    }

    @Override
    public VDJCAlignerParameters clone() {
        return new VDJCAlignerParameters(getVAlignerParameters(), getDAlignerParameters(), getJAlignerParameters(),
                getCAlignerParameters(), vjAlignmentOrder, includeDScore, includeCScore, minSumScore, maxHits,
                relativeMinVFR3CDR3Score, allowPartialAlignments, allowNoCDR3PartAlignments,
                allowChimeras, readsLayout, mergerParameters, fixSeed,vjOverlapWindow);
    }
}
