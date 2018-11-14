/*
 * Copyright (c) 2014-2018, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
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
            return GlobalObjectMappers.PRETTY.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException();
        }
    }

    public static PartialAlignmentsAssemblerParameters getDefault() {
        try {
            return GlobalObjectMappers.ONE_LINE.readValue(
                    PartialAlignmentsAssemblerParameters.class.getClassLoader().getResourceAsStream("parameters/partial_assembler_parameters.json")
                    , PartialAlignmentsAssemblerParameters.class);
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }
}
