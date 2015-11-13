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
package com.milaboratory.mixcr.reference.builder;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.milaboratory.mixcr.reference.*;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Parameters for {@link FastaLocusBuilder}.
 */
public final class FastaLocusBuilderParameters {
    private final GeneType geneType;
    private final String alleleNameExtractionPattern, functionalAllelePattern, referenceAllelePattern;
    private final char paddingChar;
    private final boolean alignAlleles;
    private final AnchorPointPosition[] anchorPointPositions;

    // Util fields
    @JsonIgnore
    private final Pattern alleleNameExtractionPatternP;
    @JsonIgnore
    private final Pattern functionalGenePatternP;
    @JsonIgnore
    private final Pattern referenceAllelePatternP;
    @JsonIgnore
    private final int[] referencePointPositions;


    public FastaLocusBuilderParameters(GeneType geneType,
                                       String alleleNameExtractionPattern,
                                       String functionalAllelePattern,
                                       String referenceAllelePattern,
                                       char paddingChar,
                                       boolean alignAlleles,
                                       AnchorPointPosition... anchorPointPositions) {
        this.geneType = geneType;
        this.alleleNameExtractionPattern = alleleNameExtractionPattern;
        this.functionalAllelePattern = functionalAllelePattern;
        this.referenceAllelePattern = referenceAllelePattern;
        this.paddingChar = paddingChar;
        this.alignAlleles = alignAlleles;
        this.anchorPointPositions = anchorPointPositions;
        for (AnchorPointPosition ap : anchorPointPositions)
            if (ap.point.getGeneType() != geneType) {
                throw new IllegalArgumentException("Anchor point " + ap.point +
                        " doesn't apply to " + geneType + " gene type.");
            }

        // Calculating output fields
        this.alleleNameExtractionPatternP = Pattern.compile(alleleNameExtractionPattern);
        this.functionalGenePatternP = Pattern.compile(functionalAllelePattern);
        this.referenceAllelePatternP = Pattern.compile(referenceAllelePattern);
        this.referencePointPositions = calculateReferencePointPositions(geneType, anchorPointPositions);
    }

    public GeneType getGeneType() {
        return geneType;
    }

    public Pattern getAlleleNameExtractionPattern() {
        return alleleNameExtractionPatternP;
    }

    public Pattern getFunctionalAllelePattern() {
        return functionalGenePatternP;
    }

    public Pattern getReferenceAllelePattern() {
        return referenceAllelePatternP;
    }

    public char getPaddingChar() {
        return paddingChar;
    }

    public boolean doAlignAlleles() {
        return alignAlleles;
    }

    /**
     * Returns positions of reference points formatted as final array that will be serialized to LociLibrary file. See
     * implementation of {@link #calculateReferencePointPositions(GeneType, AnchorPointPosition[])} for details.
     */
    public int[] getReferencePointPositions() {
        return referencePointPositions;
    }

    public static int[] calculateReferencePointPositions(GeneType geneType, AnchorPointPosition[] anchorPointPositions) {
        // Getting information about basic anchor points count and offset by gene type
        LociLibraryWriter.GeneTypeInfo info = LociLibraryWriter.getGeneTypeInfo(geneType);
        int indexOfFirstPoint = info.indexOfFirstPoint;

        int[] result = new int[info.size];

        // -1 == NA
        Arrays.fill(result, -1);

        // Filling array
        for (AnchorPointPosition ap : anchorPointPositions)
            // Reference points in allele-specific RP array are stored starting from first
            // reference point that applies to allele's gene type, so index in allele specific
            // array is calculated as globalRPIndex - indexOfFirstPointOfTHeGeneType
            result[ReferenceUtil.getReferencePointIndex(ap.point) - indexOfFirstPoint] = ap.position;

        return result;
    }

    /**
     * Represents information about reference point position in target FASTA file.
     */
    public static final class AnchorPointPosition {
        final ReferencePoint point;
        final int position;

        public AnchorPointPosition(ReferencePoint point, int position) {
            if (!point.isBasicPoint())
                throw new IllegalArgumentException("Only basic reference points are supported.");
            this.point = point;
            this.position = position;
        }
    }
}
