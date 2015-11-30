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
import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.reference.GeneType;
import com.milaboratory.mixcr.reference.LociLibraryWriter;
import com.milaboratory.mixcr.reference.ReferencePoint;
import com.milaboratory.mixcr.reference.ReferenceUtil;
import gnu.trove.impl.Constants;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Parameters for {@link FastaLocusBuilder}.
 */
public final class FastaLocusBuilderParameters {
    private final GeneType geneType;
    private final String alleleNameExtractionPattern, functionalAllelePattern, referenceAllelePattern;
    private final char paddingChar;
    private final ReferencePoint frameBoundedAnchorPoint;
    private final boolean firstOccurredAlleleIsReference;
    private final AlignmentScoring<NucleotideSequence> scoring;
    private final AnchorPointPositionInfo[] anchorPointPositions;

    // Util fields
    @JsonIgnore
    private final Pattern alleleNameExtractionPatternP;
    @JsonIgnore
    private final Pattern functionalGenePatternP;
    @JsonIgnore
    private final Pattern referenceAllelePatternP;
    @JsonIgnore
    private final int[] referencePointPositions;
    @JsonIgnore
    private final TObjectIntMap<ReferencePoint> referencePointIndexMapping;
    @JsonIgnore
    private final ReferencePoint[] indexReferencePointMapping;
    @JsonIgnore
    private final int translationReferencePointIndex;


    public FastaLocusBuilderParameters(GeneType geneType,
                                       String alleleNameExtractionPattern,
                                       String functionalAllelePattern,
                                       String referenceAllelePattern,
                                       char paddingChar,
                                       ReferencePoint frameBoundedAnchorPoint,
                                       boolean firstOccurredAlleleIsReference,
                                       AlignmentScoring<NucleotideSequence> scoring,
                                       AnchorPointPositionInfo... anchorPointPositions) {
        this.geneType = geneType;
        this.alleleNameExtractionPattern = alleleNameExtractionPattern;
        this.functionalAllelePattern = functionalAllelePattern;
        this.referenceAllelePattern = referenceAllelePattern;
        this.paddingChar = paddingChar;
        this.frameBoundedAnchorPoint = frameBoundedAnchorPoint;
        this.firstOccurredAlleleIsReference = firstOccurredAlleleIsReference;
        this.scoring = scoring;
        this.anchorPointPositions = anchorPointPositions;
        for (AnchorPointPositionInfo ap : anchorPointPositions)
            if (ap.point.getGeneType() != geneType) {
                throw new IllegalArgumentException("Anchor point " + ap.point +
                        " doesn't apply to " + geneType + " gene type.");
            }

        // Calculating output fields
        this.alleleNameExtractionPatternP = Pattern.compile(alleleNameExtractionPattern);
        this.functionalGenePatternP = Pattern.compile(functionalAllelePattern);
        this.referenceAllelePatternP = referenceAllelePattern == null ? null : Pattern.compile(referenceAllelePattern);

        // Calculating reference points positions

        // Getting information about basic anchor points count and offset by gene type
        LociLibraryWriter.GeneTypeInfo info = LociLibraryWriter.getGeneTypeInfo(geneType);
        int indexOfFirstPoint = info.indexOfFirstPoint;

        // Extracting frame bounded anchor point id
        this.translationReferencePointIndex = frameBoundedAnchorPoint == null ? -1 :
                ReferenceUtil.getReferencePointIndex(frameBoundedAnchorPoint) - indexOfFirstPoint;

        // Init
        this.referencePointPositions = new int[info.size];
        this.indexReferencePointMapping = new ReferencePoint[info.size];
        this.referencePointIndexMapping = new TObjectIntHashMap<>(Constants.DEFAULT_CAPACITY, Constants.DEFAULT_LOAD_FACTOR, -1);

        // -1 == NA
        Arrays.fill(this.referencePointPositions, -1);
        // Filling array
        for (int i = 0; i < anchorPointPositions.length; i++) {
            AnchorPointPositionInfo ap = anchorPointPositions[i];
            // Reference points in allele-specific RP array are stored starting from first
            // reference point that applies to allele's gene type, so index in allele specific
            // array is calculated as globalRPIndex - indexOfFirstPointOfTHeGeneType
            int index = ReferenceUtil.getReferencePointIndex(ap.point) - indexOfFirstPoint;
            this.referencePointPositions[index] = ap.nucleotidePattern != null ?
                    -i + AnchorPointPositionInfo.PATTERN_LINK_OFFSET : ap.position;
            this.referencePointIndexMapping.put(ap.point, index);
            this.indexReferencePointMapping[index] = ap.point;
        }
    }

    public AnchorPointPositionInfo getAnchorPointPositionInfo(int id) {
        return anchorPointPositions[id];
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

    public int getTranslationReferencePointIndex() {
        return translationReferencePointIndex;
    }

    public boolean firstOccurredAlleleIsReference() {
        return firstOccurredAlleleIsReference;
    }

    public boolean doAlignAlleles() {
        return scoring != null;
    }

    public AlignmentScoring<NucleotideSequence> getScoring() {
        return scoring;
    }

    /**
     * Returns positions of reference points formatted as final array that will be serialized to LociLibrary file. See
     * implementation of {@link #FastaLocusBuilderParameters(GeneType, String, String, String, char, ReferencePoint,
     * boolean, AlignmentScoring, AnchorPointPositionInfo...)} for details.
     */
    public int[] getReferencePointPositions() {
        return referencePointPositions;
    }

    public TObjectIntMap<ReferencePoint> getReferencePointIndexMapping() {
        return referencePointIndexMapping;
    }

    public ReferencePoint[] getIndexReferencePointMapping() {
        return indexReferencePointMapping;
    }

    /**
     * Represents information about reference point position in target FASTA file.
     */
    public static final class AnchorPointPositionInfo {
        /**
         * Special value for position field telling builder to assign this anchor point to the beginning of the input
         * sequence
         */
        public static final int BEGINNING_OF_SEQUENCE = Integer.MIN_VALUE;
        /**
         * Special value for position field telling builder to assign this anchor point to the end of the input
         * sequence
         */
        public static final int END_OF_SEQUENCE = Integer.MAX_VALUE;
        /**
         * Used to switch off position guided anchor point search
         */
        public static final int USE_ONLY_PATTERN = Integer.MIN_VALUE + 1;
        /**
         * Used internally
         */
        public static final int PATTERN_LINK_OFFSET = Integer.MIN_VALUE + 1024;
        /**
         * Name of anchor group in regex pattern
         */
        public static final String ANCHOR_GROUP_NAME = "anchor";
        final ReferencePoint point;
        final int position;
        final String nucleotidePattern;
        final Pattern nucleotidePatternP;

        public AnchorPointPositionInfo(ReferencePoint point, int position) {
            this(point, position, null);
        }

        public AnchorPointPositionInfo(ReferencePoint point, int position, String nucleotidePattern) {
            if (!point.isBasicPoint())
                throw new IllegalArgumentException("Only basic reference points are supported.");
            this.point = point;
            this.position = position;
            this.nucleotidePattern = nucleotidePattern;
            this.nucleotidePatternP = nucleotidePattern == null ? null : Pattern.compile(nucleotidePattern, Pattern.CASE_INSENSITIVE);
        }
    }
}
