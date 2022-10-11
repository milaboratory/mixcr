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
package com.milaboratory.mixcr.vdjaligners;

import com.fasterxml.jackson.annotation.*;
import com.milaboratory.core.PairedEndReadsLayout;
import com.milaboratory.core.alignment.LinearGapAlignmentScoring;
import com.milaboratory.core.merger.MergerParameters;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.HasFeatureToAlign;
import com.milaboratory.mixcr.basictypes.HasRelativeMinScore;
import com.milaboratory.primitivio.annotations.Serializable;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;

import java.util.*;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
@Serializable(asJson = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public final class VDJCAlignerParameters implements HasRelativeMinScore, HasFeatureToAlign, java.io.Serializable {
    @JsonIgnore
    private final EnumMap<GeneType, GeneAlignmentParameters> alignmentParameters;
    private VJAlignmentOrder vjAlignmentOrder;
    private VDJCLibraryStructure libraryStructure;
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
    private boolean saveOriginalSequence;
    private boolean saveOriginalReads;
    private boolean smartForceEdgeAlignments;

    @JsonCreator
    public VDJCAlignerParameters(@JsonProperty("vParameters") KGeneAlignmentParameters vParameters,
                                 @JsonProperty("dParameters") DAlignerParameters dParameters,
                                 @JsonProperty("jParameters") KGeneAlignmentParameters jParameters,
                                 @JsonProperty("cParameters") KGeneAlignmentParameters cParameters,
                                 @JsonProperty("vjAlignmentOrder") VJAlignmentOrder vjAlignmentOrder,
                                 @JsonProperty("libraryStructure") VDJCLibraryStructure libraryStructure,
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
                                 @JsonProperty("saveOriginalSequence") boolean saveOriginalSequence,
                                 @JsonProperty("saveOriginalReads") boolean saveOriginalReads,
                                 @JsonProperty("smartForceEdgeAlignments") boolean smartForceEdgeAlignments) {
        this.alignmentParameters = new EnumMap<>(GeneType.class);
        setGeneAlignerParameters(GeneType.Variable, vParameters);
        setGeneAlignerParameters(GeneType.Diversity, dParameters);
        setGeneAlignerParameters(GeneType.Joining, jParameters);
        setGeneAlignerParameters(GeneType.Constant, cParameters);
        this.vjAlignmentOrder = vjAlignmentOrder;
        this.libraryStructure = libraryStructure;
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
        this.saveOriginalSequence = saveOriginalSequence;
        this.saveOriginalReads = saveOriginalReads;
        this.smartForceEdgeAlignments = smartForceEdgeAlignments;
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
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public KGeneAlignmentParameters getVAlignerParameters() {
        return getVJCGeneAlignerParameters(GeneType.Variable);
    }

    @JsonProperty("dParameters")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public DAlignerParameters getDAlignerParameters() {
        return (DAlignerParameters) getGeneAlignerParameters(GeneType.Diversity);
    }

    @JsonProperty("jParameters")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public KGeneAlignmentParameters getJAlignerParameters() {
        return getVJCGeneAlignerParameters(GeneType.Joining);
    }

    @JsonProperty("cParameters")
    @JsonInclude(JsonInclude.Include.NON_NULL)
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

    @JsonProperty("libraryStructure")
    public VDJCLibraryStructure getLibraryStructure() {
        return libraryStructure;
    }

    public void setSmartForceEdgeAlignments(boolean smartForceEdgeAlignments) {
        this.smartForceEdgeAlignments = smartForceEdgeAlignments;
    }

    @JsonProperty("smartForceEdgeAlignments")
    public boolean isSmartForceEdgeAlignments() {
        return smartForceEdgeAlignments;
    }

    public void setVjAlignmentOrder(VJAlignmentOrder vjAlignmentOrder) {
        this.vjAlignmentOrder = vjAlignmentOrder;
    }

    public void setLibraryStructure(VDJCLibraryStructure libraryStructure) {
        this.libraryStructure = libraryStructure;
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

    public boolean isSaveOriginalSequence() {
        return saveOriginalSequence;
    }

    public VDJCAlignerParameters setSaveOriginalSequence(boolean saveOriginalSequence) {
        this.saveOriginalSequence = saveOriginalSequence;
        return this;
    }

    public boolean isSaveOriginalReads() {
        return saveOriginalReads;
    }

    public VDJCAlignerParameters setSaveOriginalReads(boolean saveOriginalReads) {
        this.saveOriginalReads = saveOriginalReads;
        return this;
    }

    public Set<GeneType> getGeneTypesWithLinearScoring() {
        final Set<GeneType> gtRequiringIndelShifts = new HashSet<>();
        for (GeneType gt : GeneType.values()) {
            GeneAlignmentParameters p = getGeneAlignerParameters(gt);
            if (p != null && p.getScoring() instanceof LinearGapAlignmentScoring)
                gtRequiringIndelShifts.add(gt);
        }
        return Collections.unmodifiableSet(gtRequiringIndelShifts);
    }

    @Override
    public float getRelativeMinScore(GeneType gt) {
        GeneAlignmentParameters ap = getGeneAlignerParameters(gt);
        return ap == null ? Float.NaN : ap.getRelativeMinScore();
    }

    @Override
    public String toString() {
        return "VDJCAlignerParameters{" +
                "alignmentParameters=" + alignmentParameters +
                ", vjAlignmentOrder=" + vjAlignmentOrder +
                ", libraryStructure=" + libraryStructure +
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
                saveOriginalSequence == that.saveOriginalSequence &&
                saveOriginalReads == that.saveOriginalReads &&
                Objects.equals(alignmentParameters, that.alignmentParameters) &&
                vjAlignmentOrder == that.vjAlignmentOrder &&
                libraryStructure == that.libraryStructure &&
                readsLayout == that.readsLayout &&
                Objects.equals(mergerParameters, that.mergerParameters) &&
                smartForceEdgeAlignments == that.smartForceEdgeAlignments;
    }

    @Override
    public int hashCode() {
        return Objects.hash(alignmentParameters, vjAlignmentOrder, libraryStructure, includeDScore,
                includeCScore, minSumScore, maxHits, relativeMinVFR3CDR3Score,
                allowPartialAlignments, allowNoCDR3PartAlignments, allowChimeras,
                readsLayout, mergerParameters, fixSeed, alignmentBoundaryTolerance,
                minChimeraDetectionScore, vjOverlapWindow,
                saveOriginalSequence, saveOriginalReads, smartForceEdgeAlignments);
    }

    @Override
    public VDJCAlignerParameters clone() {
        return new VDJCAlignerParameters(
                getVAlignerParameters() == null ? null : getVAlignerParameters().clone(),
                getDAlignerParameters() == null ? null : getDAlignerParameters().clone(),
                getJAlignerParameters() == null ? null : getJAlignerParameters().clone(),
                getCAlignerParameters() == null ? null : getCAlignerParameters().clone(),
                vjAlignmentOrder, libraryStructure, includeDScore, includeCScore, minSumScore, maxHits,
                relativeMinVFR3CDR3Score, allowPartialAlignments, allowNoCDR3PartAlignments,
                allowChimeras, readsLayout, mergerParameters, fixSeed, alignmentBoundaryTolerance,
                minChimeraDetectionScore, vjOverlapWindow, saveOriginalSequence, saveOriginalReads, smartForceEdgeAlignments);
    }
}
