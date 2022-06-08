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
package com.milaboratory.mixcr.partialassembler;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.milaboratory.core.merger.MergerParameters;
import com.milaboratory.util.GlobalObjectMappers;

import java.io.IOException;
import java.util.Objects;

@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
public class PartialAlignmentsAssemblerParameters {
    private int kValue, kOffset, minimalAssembleOverlap, minimalNOverlap;
    private float minimalAlignmentMergeIdentity;
    private MergerParameters mergerParameters;
    private long maxLeftParts;
    private int maxLeftMatches;

    @JsonCreator
    public PartialAlignmentsAssemblerParameters(
            @JsonProperty("kValue") int kValue,
            @JsonProperty("kOffset") int kOffset,
            @JsonProperty("minimalAssembleOverlap") int minimalAssembleOverlap,
            @JsonProperty("minimalNOverlap") int minimalNOverlap,
            @JsonProperty("minimalAlignmentMergeIdentity") int minimalAlignmentMergeIdentity,
            @JsonProperty("mergerParameters") MergerParameters mergerParameters,
            @JsonProperty("maxLeftParts") long maxLeftParts,
            @JsonProperty("maxLeftMatches") int maxLeftMatches) {
        this.kValue = kValue;
        this.kOffset = kOffset;
        this.minimalAssembleOverlap = minimalAssembleOverlap;
        this.minimalNOverlap = minimalNOverlap;
        this.minimalAlignmentMergeIdentity = minimalAlignmentMergeIdentity;
        this.mergerParameters = mergerParameters;
        this.maxLeftParts = maxLeftParts;
        this.maxLeftMatches = maxLeftMatches;
    }

    public int getMaxLeftMatches() {
        return maxLeftMatches;
    }

    public void setMaxLeftMatches(int maxLeftMatches) {
        this.maxLeftMatches = maxLeftMatches;
    }

    public MergerParameters getMergerParameters() {
        return mergerParameters;
    }

    public void setMergerParameters(MergerParameters mergerParameters) {
        this.mergerParameters = mergerParameters;
    }

    public long getMaxLeftParts() {
        return maxLeftParts;
    }

    public void setMaxLeftParts(long maxLeftParts) {
        this.maxLeftParts = maxLeftParts;
    }

    public int getKValue() {
        return kValue;
    }

    public void setKValue(int kValue) {
        this.kValue = kValue;
    }

    public int getMinimalNOverlap() {
        return minimalNOverlap;
    }

    public void setMinimalNOverlap(int minimalNOverlap) {
        this.minimalNOverlap = minimalNOverlap;
    }

    public float getMinimalAlignmentMergeIdentity() {
        return minimalAlignmentMergeIdentity;
    }

    public void setMinimalAlignmentMergeIdentity(float minimalAlignmentMergeIdentity) {
        this.minimalAlignmentMergeIdentity = minimalAlignmentMergeIdentity;
    }

    public int getKOffset() {
        return kOffset;
    }

    public void setKOffset(int kOffset) {
        this.kOffset = kOffset;
    }

    public int getMinimalAssembleOverlap() {
        return minimalAssembleOverlap;
    }

    public void setMinimalAssembleOverlap(int minimalAssembleOverlap) {
        this.minimalAssembleOverlap = minimalAssembleOverlap;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PartialAlignmentsAssemblerParameters that = (PartialAlignmentsAssemblerParameters) o;
        return kValue == that.kValue &&
                kOffset == that.kOffset &&
                minimalAssembleOverlap == that.minimalAssembleOverlap &&
                minimalNOverlap == that.minimalNOverlap &&
                Float.compare(that.minimalAlignmentMergeIdentity, minimalAlignmentMergeIdentity) == 0 &&
                maxLeftParts == that.maxLeftParts &&
                maxLeftMatches == that.maxLeftMatches &&
                Objects.equals(mergerParameters, that.mergerParameters);
    }

    @Override
    public int hashCode() {

        return Objects.hash(kValue, kOffset, minimalAssembleOverlap, minimalNOverlap, minimalAlignmentMergeIdentity, mergerParameters, maxLeftParts, maxLeftMatches);
    }

    @Override
    public String toString() {
        try {
            return GlobalObjectMappers.getPretty().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException();
        }
    }

    public static PartialAlignmentsAssemblerParameters getDefault() {
        try {
            return GlobalObjectMappers.getOneLine().readValue(
                    PartialAlignmentsAssemblerParameters.class.getClassLoader().getResourceAsStream("parameters/partial_assembler_parameters.json")
                    , PartialAlignmentsAssemblerParameters.class);
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }
}
