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

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.milaboratory.core.PairedEndReadsLayout;
import com.milaboratory.core.merger.MergerParameters;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.HasFeatureToAlign;
import com.milaboratory.primitivio.annotations.Serializable;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
@Serializable(asJson = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class VDJCAlignerParameters implements HasFeatureToAlign, java.io.Serializable {
    @JsonIgnore
    private final EnumMap<GeneType, GeneAlignmentParameters> alignmentParameters;
    private VJAlignmentOrder vjAlignmentOrder;
    private boolean includeDScore, includeCScore;
    private float minSumScore;
    private int maxHits;
    private float relativeMinVFR3CDR3Score;
    private boolean allowPartialAlignments, allowNoCDR3PartAlignments, allowChimeras;
    private PairedEndReadsLayout readsLayout;
    private MergerParameters mergerParameters;
    private boolean fixSeed;
    private int alignmentBoundaryTolerance;
    private int minChimeraDetectionScore;
    private int vjOverlapWindow;
    private boolean saveOriginalReads;

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
                                 @JsonProperty("alignmentBoundaryTolerance") int alignmentBoundaryTolerance,
                                 @JsonProperty("minChimeraDetectionScore") int minChimeraDetectionScore,
                                 @JsonProperty("vjOverlapWindow") int vjOverlapWindow,
                                 @JsonProperty("saveOriginalReads") boolean saveOriginalReads) {
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
        this.alignmentBoundaryTolerance = alignmentBoundaryTolerance;
        this.minChimeraDetectionScore = minChimeraDetectionScore;
        this.vjOverlapWindow = vjOverlapWindow;
        this.saveOriginalReads = saveOriginalReads;
    }

    public int getVJOverlapWindow() {
        return vjOverlapWindow;
    }

    public VDJCAlignerParameters setVJOverlapWindow(int vjOverlapWindow) {
        this.vjOverlapWindow = vjOverlapWindow;
        return this;
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

    public NucleotideSequence extractFeatureToAlign(VDJCGene gene) {
        GeneFeature featureToAlign = getFeatureToAlign(gene.getGeneType());
        return featureToAlign == null ? null : gene.getFeature(featureToAlign);
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

    public EnumMap<GeneType, GeneFeature> getFeaturesToAlignMap() {
        EnumMap<GeneType, GeneFeature> res = new EnumMap<>(GeneType.class);
        for (GeneType gt : GeneType.VDJC_REFERENCE)
            res.put(gt, getFeatureToAlign(gt));
        return res;
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

    public int getAlignmentBoundaryTolerance() {
        return alignmentBoundaryTolerance;
    }

    public VDJCAlignerParameters setAlignmentBoundaryTolerance(int alignmentBoundaryTolerance) {
        this.alignmentBoundaryTolerance = alignmentBoundaryTolerance;
        return this;
    }

    public PairedEndReadsLayout getReadsLayout() {
        return readsLayout;
    }

    public VDJCAlignerParameters setReadsLayout(PairedEndReadsLayout readsLayout) {
        this.readsLayout = readsLayout;
        return this;
    }

    public int getMinChimeraDetectionScore() {
        return minChimeraDetectionScore;
    }

    public VDJCAlignerParameters setMinChimeraDetectionScore(int minChimeraDetectionScore) {
        this.minChimeraDetectionScore = minChimeraDetectionScore;
        return this;
    }

    public void setMergerParameters(MergerParameters mergerParameters) {
        this.mergerParameters = mergerParameters;
    }

    public MergerParameters getMergerParameters() {
        return mergerParameters;
    }

    public boolean isSaveOriginalReads() {
        return saveOriginalReads;
    }

    public VDJCAlignerParameters setSaveOriginalReads(boolean saveOriginalReads) {
        this.saveOriginalReads = saveOriginalReads;
        return this;
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
                ", alignmentBoundaryTolerance=" + alignmentBoundaryTolerance +
                ", minChimeraDetectionScore=" + minChimeraDetectionScore +
                ", vjOverlapWindow=" + vjOverlapWindow +
                ", saveOriginalReads=" + saveOriginalReads +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VDJCAlignerParameters)) return false;
        VDJCAlignerParameters that = (VDJCAlignerParameters) o;
        return includeDScore == that.includeDScore &&
                includeCScore == that.includeCScore &&
                Float.compare(that.minSumScore, minSumScore) == 0 &&
                maxHits == that.maxHits &&
                Float.compare(that.relativeMinVFR3CDR3Score, relativeMinVFR3CDR3Score) == 0 &&
                allowPartialAlignments == that.allowPartialAlignments &&
                allowNoCDR3PartAlignments == that.allowNoCDR3PartAlignments &&
                allowChimeras == that.allowChimeras &&
                fixSeed == that.fixSeed &&
                alignmentBoundaryTolerance == that.alignmentBoundaryTolerance &&
                minChimeraDetectionScore == that.minChimeraDetectionScore &&
                vjOverlapWindow == that.vjOverlapWindow &&
                saveOriginalReads == that.saveOriginalReads &&
                Objects.equals(alignmentParameters, that.alignmentParameters) &&
                vjAlignmentOrder == that.vjAlignmentOrder &&
                readsLayout == that.readsLayout &&
                Objects.equals(mergerParameters, that.mergerParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(alignmentParameters, vjAlignmentOrder, includeDScore, includeCScore, minSumScore, maxHits, relativeMinVFR3CDR3Score, allowPartialAlignments, allowNoCDR3PartAlignments, allowChimeras, readsLayout, mergerParameters, fixSeed, alignmentBoundaryTolerance, minChimeraDetectionScore, vjOverlapWindow, saveOriginalReads);
    }

    @Override
    public VDJCAlignerParameters clone() {
        return new VDJCAlignerParameters(getVAlignerParameters(), getDAlignerParameters(), getJAlignerParameters(),
                getCAlignerParameters(), vjAlignmentOrder, includeDScore, includeCScore, minSumScore, maxHits,
                relativeMinVFR3CDR3Score, allowPartialAlignments, allowNoCDR3PartAlignments,
                allowChimeras, readsLayout, mergerParameters, fixSeed, alignmentBoundaryTolerance,
                minChimeraDetectionScore, vjOverlapWindow, saveOriginalReads);
    }
}
