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

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.milaboratory.core.alignment.AlignmentScoring;
import com.milaboratory.core.sequence.NucleotideSequence;
import io.repseq.reference.GeneType;
import io.repseq.reference.LociLibraryWriter;
import io.repseq.reference.ReferencePoint;
import io.repseq.reference.ReferenceUtil;
import gnu.trove.impl.Constants;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;

import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Pattern;

/**
 * Parameters for {@link FastaLocusBuilder}.
 */
@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, isGetterVisibility = JsonAutoDetect.Visibility.NONE,
        getterVisibility = JsonAutoDetect.Visibility.NONE)
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

    @JsonCreator
    public FastaLocusBuilderParameters(@JsonProperty("geneType") GeneType geneType,
                                       @JsonProperty("alleleNameExtractionPattern") String alleleNameExtractionPattern,
                                       @JsonProperty("functionalAllelePattern") String functionalAllelePattern,
                                       @JsonProperty("referenceAllelePattern") String referenceAllelePattern,
                                       @JsonProperty("paddingChar") char paddingChar,
                                       @JsonProperty("frameBoundedAnchorPoint") ReferencePoint frameBoundedAnchorPoint,
                                       @JsonProperty("firstOccurredAlleleIsReference") boolean firstOccurredAlleleIsReference,
                                       @JsonProperty("scoring") AlignmentScoring<NucleotideSequence> scoring,
                                       @JsonProperty("anchorPointPositions") AnchorPointPositionInfo... anchorPointPositions) {
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
        LociLibraryWriter.GeneTypeInfo info = LociLibraryWriter.getGeneTypeInfo(geneType, false);
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        FastaLocusBuilderParameters that = (FastaLocusBuilderParameters) o;

        if (paddingChar != that.paddingChar) return false;
        if (firstOccurredAlleleIsReference != that.firstOccurredAlleleIsReference) return false;
        if (geneType != that.geneType) return false;
        if (alleleNameExtractionPattern != null ? !alleleNameExtractionPattern.equals(that.alleleNameExtractionPattern) : that.alleleNameExtractionPattern != null)
            return false;
        if (functionalAllelePattern != null ? !functionalAllelePattern.equals(that.functionalAllelePattern) : that.functionalAllelePattern != null)
            return false;
        if (referenceAllelePattern != null ? !referenceAllelePattern.equals(that.referenceAllelePattern) : that.referenceAllelePattern != null)
            return false;
        if (frameBoundedAnchorPoint != null ? !frameBoundedAnchorPoint.equals(that.frameBoundedAnchorPoint) : that.frameBoundedAnchorPoint != null)
            return false;
        if (scoring != null ? !scoring.equals(that.scoring) : that.scoring != null) return false;
        // Probably incorrect - comparing Object[] arrays with Arrays.equals
        return Arrays.equals(anchorPointPositions, that.anchorPointPositions);

    }

    @Override
    public int hashCode() {
        int result = geneType != null ? geneType.hashCode() : 0;
        result = 31 * result + (alleleNameExtractionPattern != null ? alleleNameExtractionPattern.hashCode() : 0);
        result = 31 * result + (functionalAllelePattern != null ? functionalAllelePattern.hashCode() : 0);
        result = 31 * result + (referenceAllelePattern != null ? referenceAllelePattern.hashCode() : 0);
        result = 31 * result + (int) paddingChar;
        result = 31 * result + (frameBoundedAnchorPoint != null ? frameBoundedAnchorPoint.hashCode() : 0);
        result = 31 * result + (firstOccurredAlleleIsReference ? 1 : 0);
        result = 31 * result + (scoring != null ? scoring.hashCode() : 0);
        result = 31 * result + Arrays.hashCode(anchorPointPositions);
        return result;
    }

    /**
     * Represents information about reference point position in target FASTA file.
     */
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY, isGetterVisibility = JsonAutoDetect.Visibility.NONE,
            getterVisibility = JsonAutoDetect.Visibility.NONE)
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
        //public static final String ANCHOR_GROUP_NAME = "anchor";
        final ReferencePoint point;
        @JsonSerialize(using = PositionSerializer.class)
        @JsonDeserialize(using = PositionDeserializer.class)
        final int position;

        @JsonSerialize(include = JsonSerialize.Inclusion.NON_NULL)
        final String nucleotidePattern;

        @JsonIgnore
        final Pattern nucleotidePatternP;

        public AnchorPointPositionInfo(ReferencePoint point, int position) {
            this(point, position, null);
        }

        @JsonCreator
        public AnchorPointPositionInfo(@JsonProperty("point") ReferencePoint point,
                                       @JsonProperty("position") int position,
                                       @JsonProperty("nucleotidePattern") String nucleotidePattern) {
            if (!point.isBasicPoint())
                throw new IllegalArgumentException("Only basic reference points are supported.");
            this.point = point;
            this.position = position;
            this.nucleotidePattern = nucleotidePattern;
            this.nucleotidePatternP = nucleotidePattern == null ? null : Pattern.compile(nucleotidePattern, Pattern.CASE_INSENSITIVE);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            AnchorPointPositionInfo that = (AnchorPointPositionInfo) o;

            if (position != that.position) return false;
            if (!point.equals(that.point)) return false;
            return !(nucleotidePattern != null ? !nucleotidePattern.equals(that.nucleotidePattern) : that.nucleotidePattern != null);

        }

        @Override
        public int hashCode() {
            int result = point.hashCode();
            result = 31 * result + position;
            result = 31 * result + (nucleotidePattern != null ? nucleotidePattern.hashCode() : 0);
            return result;
        }
    }

    public static final class PositionSerializer extends JsonSerializer<Integer> {
        @Override
        public void serialize(Integer value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {
            if (value == AnchorPointPositionInfo.BEGINNING_OF_SEQUENCE)
                jgen.writeString("begin");
            else if (value == AnchorPointPositionInfo.END_OF_SEQUENCE)
                jgen.writeString("end");
            else if (value == AnchorPointPositionInfo.USE_ONLY_PATTERN)
                jgen.writeString("no");
            else
                jgen.writeNumber(value);
        }
    }

    public static final class PositionDeserializer extends JsonDeserializer<Integer> {
        @Override
        public Integer deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException, JsonProcessingException {
            switch (jp.getCurrentToken()) {
                case VALUE_NUMBER_INT:
                    return jp.getIntValue();
                case VALUE_STRING:
                    String str = jp.getValueAsString();
                    switch (str) {
                        case "begin":
                            return AnchorPointPositionInfo.BEGINNING_OF_SEQUENCE;
                        case "end":
                            return AnchorPointPositionInfo.END_OF_SEQUENCE;
                        case "no":
                            return AnchorPointPositionInfo.USE_ONLY_PATTERN;
                    }
            }
            throw new IllegalArgumentException("Wrong position.");
        }
    }
}
