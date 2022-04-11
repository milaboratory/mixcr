/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
package com.milaboratory.mixcr.alleles;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.primitivio.annotations.Serializable;

import java.util.Objects;

/**
 *
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
@Serializable(asJson = true)
public class FindAllelesParameters implements java.io.Serializable {
    public final int minDiversityToSearchAlleles;
    public final int minDiversityToFindSecondAllele;
    /**
     * Clone will be accepted if distanceFromRoot / changeOfDistanceOnCloneAdd >= thresholdForFreeClones.
     */
    public final double minPartOfClonesToDeterminateAllele;
    /**
     * Max penalty by mutations count in allele to determinate is this clone have the allele.
     */
    public final double maxPenaltyByAlleleMutation;
    /**
     * Use only productive clonotypes (no OOF, no stops).
     */
    public final boolean productiveOnly;
    /**
     * Min portion of clones to determinate common alignment ranges.
     */
    public final double minPortionOfClonesForCommonAlignmentRanges;

    @JsonCreator
    public FindAllelesParameters(
            @JsonProperty("minDiversityToSearchAlleles") int minDiversityToSearchAlleles,
            @JsonProperty("minDiversityToFindSecondAllele") int minDiversityToFindSecondAllele,
            @JsonProperty("minPartOfClonesToDeterminateAllele") double minPartOfClonesToDeterminateAllele,
            @JsonProperty("maxPenaltyByAlleleMutation") double maxPenaltyByAlleleMutation,
            @JsonProperty("productiveOnly") boolean productiveOnly,
            @JsonProperty("minPortionOfClonesForCommonAlignmentRanges") double minPortionOfClonesForCommonAlignmentRanges
    ) {
        this.minDiversityToSearchAlleles = minDiversityToSearchAlleles;
        this.minDiversityToFindSecondAllele = minDiversityToFindSecondAllele;
        this.minPartOfClonesToDeterminateAllele = minPartOfClonesToDeterminateAllele;
        this.maxPenaltyByAlleleMutation = maxPenaltyByAlleleMutation;
        this.productiveOnly = productiveOnly;
        this.minPortionOfClonesForCommonAlignmentRanges = minPortionOfClonesForCommonAlignmentRanges;
    }

    @Override
    public FindAllelesParameters clone() {
        return new FindAllelesParameters(minDiversityToSearchAlleles, minDiversityToFindSecondAllele, minPartOfClonesToDeterminateAllele, maxPenaltyByAlleleMutation, productiveOnly, minPortionOfClonesForCommonAlignmentRanges);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FindAllelesParameters that = (FindAllelesParameters) o;
        return minDiversityToSearchAlleles == that.minDiversityToSearchAlleles && minDiversityToFindSecondAllele == that.minDiversityToFindSecondAllele && Double.compare(that.minPartOfClonesToDeterminateAllele, minPartOfClonesToDeterminateAllele) == 0 && Double.compare(that.maxPenaltyByAlleleMutation, maxPenaltyByAlleleMutation) == 0 && productiveOnly == that.productiveOnly && Double.compare(that.minPortionOfClonesForCommonAlignmentRanges, minPortionOfClonesForCommonAlignmentRanges) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minDiversityToSearchAlleles, minDiversityToFindSecondAllele, minPartOfClonesToDeterminateAllele, maxPenaltyByAlleleMutation, productiveOnly, minPortionOfClonesForCommonAlignmentRanges);
    }
}
