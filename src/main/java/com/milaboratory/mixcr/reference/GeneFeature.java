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
package com.milaboratory.mixcr.reference;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.milaboratory.mixcr.util.ParseUtil;
import com.milaboratory.primitivio.annotations.Serializable;
import com.milaboratory.util.ArrayIterator;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

import static com.milaboratory.mixcr.reference.ReferencePoint.*;

//DRegion
//DRegion(-10, +6)
//DRegionBegin(-10):JRegionEnd(+6)
//DRegionBegin(-10):DRegionBegin(+6)
//DRegionBegin(-10, +6)

//[DRegionBegin(-10, +6), DRegionBegin(-10, +6)]
@JsonDeserialize(using = GeneFeature.Deserializer.class)
@JsonSerialize(using = GeneFeature.Serializer.class)
@Serializable(by = IO.GeneFeatureSerializer.class)
public final class GeneFeature implements Iterable<GeneFeature.ReferenceRange>, Comparable<GeneFeature>,
        java.io.Serializable {
    /* V, D, J, Regions */

    /**
     * Full V Region
     */
    public static final GeneFeature VRegion = new GeneFeature(FR1Begin, VEnd),
    /**
     * Full V Region trimmed
     */
    VRegionTrimmed = new GeneFeature(FR1Begin, VEndTrimmed),
    /**
     * Full D Region
     */
    DRegion = new GeneFeature(DBegin, DEnd),
    /**
     * Full D Region trimmed
     */
    DCDR3Part = new GeneFeature(DBeginTrimmed, DEndTrimmed),
    /**
     * Full J Region
     */
    JRegion = new GeneFeature(JBegin, FR4End),
    /**
     * Full J Region trimmed
     */
    JRegionTrimmed = new GeneFeature(JBeginTrimmed, FR4End),

    /* Major gene parts */

    /**
     * 5'UTR not trimmed
     */
    V5UTRGermline = new GeneFeature(UTR5Begin, V5UTREnd),
    /**
     * 5'UTR trimmed
     */
    V5UTR = new GeneFeature(V5UTRBeginTrimmed, V5UTREnd),
    /**
     * Part of lider sequence in first exon. The same as {@code Exon1}.
     */
    L1 = new GeneFeature(L1Begin, L1End),
    /**
     * Intron in V region.
     */
    Intron = new GeneFeature(VIntronBegin, VIntronEnd),
    /**
     * Part of lider sequence in second exon.
     */
    L2 = new GeneFeature(L2Begin, L2End),
    /**
     * {@code L1} + {@code Intron} + {@code L2}
     */
    VLIntronL = new GeneFeature(L1Begin, L2End),

    /* Frameworks and CDRs */

    /**
     * Framework 1
     */
    FR1 = new GeneFeature(FR1Begin, FR1End),
    /**
     * CDR1 (Complementarity determining region 1)
     */
    CDR1 = new GeneFeature(CDR1Begin, CDR1End),
    /**
     * Framework 2
     */
    FR2 = new GeneFeature(FR2Begin, FR2End),
    /**
     * CDR2 (Complementarity determining region 2)
     */
    CDR2 = new GeneFeature(CDR2Begin, CDR2End),
    /**
     * Framework 2
     */
    FR3 = new GeneFeature(FR3Begin, FR3End),
    /**
     * CDR3 (Complementarity determining region 3). Cys from V region and Phe/Trp from J region included.
     */
    CDR3 = new GeneFeature(CDR3Begin, CDR3End),
    /**
     * CDR3 (Complementarity determining region 3). Cys from V region and Phe/Trp from J region excluded.
     */
    ShortCDR3 = new GeneFeature(CDR3, +3, -3),
    /**
     * Framework 4 (J region after CDR3)
     */
    FR4 = new GeneFeature(FR4Begin, FR4End),

    /* Subregions of CDR3 */

    /**
     * Part of V region inside CDR3 (commonly starts from Cys)
     */
    VCDR3Part = new GeneFeature(CDR3Begin, VEndTrimmed),
    /**
     * Part of J region inside CDR3 (commonly ends with Phe/Trp)
     */
    JCDR3Part = new GeneFeature(JBeginTrimmed, CDR3End),
    /**
     * Part of V region inside CDR3 (commonly starts from Cys)
     */
    GermlineVCDR3Part = new GeneFeature(CDR3Begin, VEnd),
    /**
     * Part of J region inside CDR3 (commonly ends with Phe/Trp)
     */
    GermlineJCDR3Part = new GeneFeature(JBegin, CDR3End),
    /**
     * N region between V and D genes. Is not defined for loci without D genes and for V(D)J rearrangement with
     * unidentified D region.
     */
    VDJunction = new GeneFeature(VEndTrimmed, DBeginTrimmed),
    /**
     * N region between V and D genes. Is not defined for loci without D genes and for V(D)J rearrangement with
     * unidentified D region.
     */
    DJJunction = new GeneFeature(DEndTrimmed, JBeginTrimmed),
    /**
     * Region between V and J regions. For loci without D genes fully composed from non-template nucleotides. May
     * contain D region.
     */
    VJJunction = new GeneFeature(VEndTrimmed, JBeginTrimmed),

    /* Exons. */

    /**
     * First exon. The same as {@code L1}.
     */
    Exon1 = new GeneFeature(L1Begin, L1End),
    /**
     * Full second exon of IG/TCR gene.
     */
    Exon2 = new GeneFeature(L2Begin, FR4End),

    /* Region Exons */

    /**
     * Second exon of V gene. Ends within CDR3 in V(D)J rearrangement.
     */
    VExon2 = new GeneFeature(L2Begin, VEnd),

    /**
     * Second exon of V gene trimmed. Ends within CDR3 in V(D)J rearrangement.
     */
    VExon2Trimmed = new GeneFeature(L2Begin, VEndTrimmed),

    /* C Region */

    /**
     * First exon of C Region
     */
    CExon1 = new GeneFeature(CBegin, CExon1End),

    /**
     * Full C region
     */
    CRegion = new GeneFeature(CBegin, CEnd),

    /* Composite features */

    /**
     * Full leader sequence
     */
    L = new GeneFeature(L1, L2),
    /**
     * {@code Exon1} + {@code VExon2}. Common reference feature used in alignments for mRNA data obtained without
     * 5'RACE.
     */
    VTranscriptWithout5UTR = new GeneFeature(Exon1, VExon2),
    /**
     * {@code V5UTR} + {@code Exon1} + {@code VExon2}. Common reference feature used in alignments for cDNA data
     * obtained using 5'RACE (that may contain UTRs).
     */
    VTranscript = new GeneFeature(V5UTRGermline, Exon1, VExon2),
    /**
     * {@code {V5UTRBegin:VEnd}}. Common reference feature used in alignments for genomic DNA data.
     */
    VGene = new GeneFeature(UTR5Begin, VEnd),
    /**
     * First two exons of IG/TCR gene.
     */
    VDJTranscriptWithout5UTR = new GeneFeature(Exon1, Exon2),
    /**
     * First two exons with 5'UTR of IG/TCR gene.
     */
    VDJTranscript = new GeneFeature(V5UTRGermline, Exon1, Exon2),

    /* Full length assembling features */
    /**
     * Full V, D, J assembly without 5'UTR and leader sequence.
     */
    VDJRegion = new GeneFeature(FR1Begin, FR4End);


    //regions are sorted in natural ordering using indexes
    final ReferenceRange[] regions;

    public GeneFeature(final GeneFeature... features) {
        if (features.length == 0)
            throw new IllegalArgumentException();
        int total = 0;
        for (GeneFeature feature : features)
            total += feature.regions.length;
        ReferenceRange[] regions = new ReferenceRange[total];
        int offset = 0;
        for (GeneFeature feature : features) {
            System.arraycopy(feature.regions, 0, regions, offset, feature.regions.length);
            offset += feature.regions.length;
        }
        this.regions = merge(regions);
    }

    public GeneFeature(ReferencePoint begin, ReferencePoint end) {
        this.regions = new ReferenceRange[]{
                new ReferenceRange(begin, end)};
    }

    public GeneFeature(GeneFeature feature, int leftOffset, int rightOffset) {
        this.regions = feature.regions.clone();
        ReferenceRange r = this.regions[0];
        this.regions[0] = new ReferenceRange(r.begin.move(leftOffset), r.end);
        r = this.regions[this.regions.length - 1];
        this.regions[this.regions.length - 1] = new ReferenceRange(r.begin, r.end.move(rightOffset));
    }

    public GeneFeature(ReferencePoint referencePoint, int leftOffset, int rightOffset) {
        this.regions = new ReferenceRange[]{
                new ReferenceRange(referencePoint.move(leftOffset), referencePoint.move(rightOffset))};
    }

    private GeneFeature(ReferenceRange range) {
        this.regions = new ReferenceRange[]{range};
    }

    GeneFeature(ReferenceRange[] regions, boolean unsafe) {
        assert unsafe;
        this.regions = regions;
    }

    public ReferenceRange getReferenceRange(int i) {
        return regions[i];
    }

    public int size() {
        return regions.length;
    }

    public GeneType getGeneType() {
        GeneType gt = regions[0].getGeneType(), tmp;

        if (gt == null)
            return null;

        for (int i = 1; i < regions.length; i++)
            if (regions[i].getGeneType() != gt)
                return null;

        return gt;
    }

    public boolean isComposite() {
        return regions.length != 1;
    }

    @Override
    public String toString() {
        return Arrays.toString(regions);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GeneFeature feature = (GeneFeature) o;

        return Arrays.equals(regions, feature.regions);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(regions);
    }

    public static GeneFeature intersection(GeneFeature gf1, GeneFeature gf2) {
        ReferencePoint firstReferencePoint1 = gf1.regions[0].begin;
        ReferencePoint firstReferencePoint2 = gf2.regions[0].begin;
        if (firstReferencePoint1.compareTo(firstReferencePoint2) > 0)
            return intersection(gf2, gf1);
        int rangePointer1 = 0;
        while (gf1.regions[rangePointer1].end.compareTo(firstReferencePoint2) <= 0)
            if (++rangePointer1 == gf1.regions.length)
                return null;

        if (gf1.regions[rangePointer1].begin.compareTo(firstReferencePoint2) > 0)
            throw new IllegalArgumentException();

        ArrayList<ReferenceRange> result = new ArrayList<>();
//        result.add(new ReferenceRange(firstReferencePoint2, gf1.regions[rangePointer1].end));
//
//        ++rangePointer1;
//        int rangePointer2 = 1;
        int rangePointer2 = 0;

        while (true) {
            if (rangePointer1 == gf1.regions.length || rangePointer2 == gf2.regions.length)
                break;

            if (rangePointer2 != 0 &&
                    !gf1.regions[rangePointer1].begin.equals(gf2.regions[rangePointer2].begin))
                throw new IllegalArgumentException();

            int c = gf1.regions[rangePointer1].end.compareTo(gf2.regions[rangePointer2].end);
            ReferencePoint maxBegin = max(gf1.regions[rangePointer1].begin, gf2.regions[rangePointer2].begin);
            if (c != 0) {
                if (c > 0) {
                    result.add(new ReferenceRange(maxBegin,
                            gf2.regions[rangePointer2].end));
                    if (rangePointer2 != gf2.regions.length - 1)
                        throw new IllegalArgumentException();
                    break;
                } else {
                    result.add(new ReferenceRange(maxBegin,
                            gf1.regions[rangePointer1].end));
                    if (rangePointer1 != gf1.regions.length - 1)
                        throw new IllegalArgumentException();
                    break;
                }
            } else
                result.add(new ReferenceRange(maxBegin, gf1.regions[rangePointer1].end));

            ++rangePointer1;
            ++rangePointer2;
        }

        return new GeneFeature(result.toArray(new ReferenceRange[result.size()]), true);
    }

    public ReferencePoint getFirstPoint() {
        return regions[0].begin;
    }

    public ReferencePoint getLastPoint() {
        return regions[regions.length - 1].end;
    }

    private static ReferencePoint min(ReferencePoint p1, ReferencePoint p2) {
        if (p1.compareTo(p2) > 0)
            return p2;
        else
            return p1;
    }

    private static ReferencePoint max(ReferencePoint p1, ReferencePoint p2) {
        if (p1.compareTo(p2) > 0)
            return p1;
        else
            return p2;
    }

    public boolean contains(GeneFeature geneFeature) {
        if (geneFeature.isComposite())
            throw new RuntimeException("Composite features are not supported.");
        for (ReferenceRange region : regions)
            if (region.contains(geneFeature.regions[0]))
                return true;
        return false;
    }

    private static ReferenceRange[] merge(final ReferenceRange[] ranges) {
        if (ranges.length == 1)
            return ranges;
        Arrays.sort(ranges, ReferenceRange.BEGIN_COMPARATOR);
        ArrayList<ReferenceRange> result = new ArrayList<>(ranges.length);

        ReferenceRange prev = ranges[0], cur;
        for (int i = 1; i < ranges.length; ++i) {
            cur = ranges[i];
            if (cur.begin.getIndex() < prev.end.getIndex())
                throw new IllegalArgumentException("Intersecting ranges.");
            if (cur.begin.getIndex() == prev.end.getIndex()
                    && prev.end.getOffset() - cur.begin.getOffset() >= 0) {
                //merge
                prev = new ReferenceRange(prev.begin, cur.end);
            } else {
                result.add(prev);
                prev = cur;
            }
        }
        result.add(prev);
        if (result.size() == ranges.length)
            return ranges;
        return result.toArray(new ReferenceRange[result.size()]);
    }

    @Override
    public int compareTo(GeneFeature o) {
        return getFirstPoint().compareTo(o.getFirstPoint());
    }

    @Override
    public Iterator<ReferenceRange> iterator() {
        return new ArrayIterator<>(regions);
    }

    @Serializable(by = IO.GeneFeatureReferenceRangeSerializer.class)
    public static final class ReferenceRange implements java.io.Serializable {
        //sorting using only begin index
        private static final Comparator<ReferenceRange> BEGIN_COMPARATOR = new Comparator<ReferenceRange>() {
            @Override
            public int compare(ReferenceRange o1, ReferenceRange o2) {
                return o1.begin.basicPoint.compareTo(o2.begin.basicPoint);
            }
        };

        public final ReferencePoint begin, end;

        ReferenceRange(ReferencePoint begin, ReferencePoint end) {
            this.begin = begin;
            this.end = end;
        }

        public GeneType getGeneType() {
            GeneType gt = begin.getGeneType();
            if (gt == null)
                return null;
            if (gt != end.getGeneType())
                return null;
            return gt;
        }

        public boolean hasOffsets() {
            return begin.offset != 0 || end.offset != 0;
        }

        public ReferenceRange getWithoutOffset() {
            if (!hasOffsets())
                return this;
            return new ReferenceRange(begin.getWithoutOffset(), end.getWithoutOffset());
        }

        public boolean contains(ReferenceRange range) {
            return range.begin.compareTo(begin) >= 0 && range.end.compareTo(end) <= 0;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            ReferenceRange range = (ReferenceRange) o;

            return begin.equals(range.begin) && end.equals(range.end);
        }

        @Override
        public int hashCode() {
            int result = begin.hashCode();
            result = 31 * result + end.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "[" + begin + ", " + end + "]";
        }
    }

    public static GeneFeature getRegion(GeneType type) {
        switch (type) {
            case Variable:
                return VRegion;
            case Diversity:
                return DRegion;
            case Joining:
                return JRegion;
            case Constant:
                return CRegion;
        }
        throw new RuntimeException();
    }

    static Map<String, GeneFeature> featuresByName = null;
    static Map<GeneFeature, String> nameByFeature = null;

    private static void ensureInitialized() {
        if (featuresByName == null) {
            synchronized (GeneFeature.class) {
                if (featuresByName == null) {
                    try {
                        Map<String, GeneFeature> fbn = new HashMap<>();
                        Map<GeneFeature, String> nbf = new HashMap<>();
                        Field[] declaredFields = GeneFeature.class.getDeclaredFields();
                        for (Field field : declaredFields)
                            if (Modifier.isStatic(field.getModifiers()) &&
                                    field.getType() == GeneFeature.class) {
                                GeneFeature value = (GeneFeature) field.get(null);
                                String name = field.getName();
                                fbn.put(name.toLowerCase(), value);
                                nbf.put(value, name);
                            }
                        featuresByName = fbn;
                        nameByFeature = nbf;
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    public static GeneFeature getFeatureByName(String pointName) {
        ensureInitialized();
        return featuresByName.get(pointName.toLowerCase());
    }

    public static String getNameByFeature(GeneFeature point) {
        ensureInitialized();
        return nameByFeature.get(point);
    }


    public static GeneFeature parse(String string) {
        string = string.replaceAll(" ", "");

        String[] singles = ParseUtil.splitWithBrackets(string, '+', "(){}");
        ArrayList<GeneFeature> features = new ArrayList<>(singles.length);
        for (String single : singles)
            features.add(parseSingle(single));
        return new GeneFeature(features.toArray(new GeneFeature[features.size()]));
    }

    public static Map<String, GeneFeature> getFeaturesByName() {
        ensureInitialized();
        return Collections.unmodifiableMap(featuresByName);
    }

    public static Map<GeneFeature, String> getNameByFeature() {
        ensureInitialized();
        return Collections.unmodifiableMap(nameByFeature);
    }

    private static GeneFeature parseSingle(String string) {
        string = string.trim();
        //single feature
        if (string.charAt(0) == '{') { // feature by points {from:to}
            if (string.charAt(string.length() - 1) != '}')
                throw new IllegalArgumentException("Incorrect input: " + string);
            string = string.substring(1, string.length() - 1);
            String[] fromTo = string.split(":");
            if (fromTo.length != 2)
                throw new IllegalArgumentException("Incorrect input: " + string);
            return new GeneFeature(ReferencePoint.parse(fromTo[0]), ReferencePoint.parse(fromTo[1]));
        } else { // feature by name CDR2(-2,3)
            int br = string.indexOf('(');

            if (br == -1) {
                GeneFeature base;
                base = getFeatureByName(string);
                if (base == null)
                    throw new IllegalArgumentException("Unknown feature: " + string);
                return base;
            } else {
                if (string.charAt(string.length() - 1) != ')')
                    throw new IllegalArgumentException("Wrong syntax: " + string);

                Object base;

                String baseName = string.substring(0, br);

                base = getFeatureByName(baseName);

                if (base == null)
                    base = ReferencePoint.getPointByName(baseName);

                if (base == null)
                    throw new IllegalArgumentException("Unknown feature / anchor point: " + baseName);

                int offset1, offset2;
                String[] offsets = string.substring(br + 1, string.length() - 1).split(",");
                try {
                    offset1 = Integer.parseInt(offsets[0].trim());
                    offset2 = Integer.parseInt(offsets[1].trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Incorrect input: " + string);
                }
                if (base instanceof GeneFeature)
                    return new GeneFeature((GeneFeature) base, offset1, offset2);
                else
                    return new GeneFeature((ReferencePoint) base, offset1, offset2);
            }
        }
    }

    public static String encode(GeneFeature feature) {
        return encode(feature, true);
    }

    static String encode(GeneFeature feature, boolean copact) {
        ensureInitialized();
        if (copact) {
            String s = nameByFeature.get(feature);
            if (s != null)
                return s;
        }
        Collection<GeneFeature> available = featuresByName.values();
        final String[] encodes = new String[feature.regions.length];
        out:
        for (int i = 0; i < encodes.length; ++i) {
            ReferenceRange region = feature.regions[i];
            if (copact) {
                String base = null;
                for (GeneFeature a : available)
                    if (match(region, a)) {
                        GeneFeature known = new GeneFeature(region.getWithoutOffset());
                        base = getNameByFeature(known);
                    }

                if (region.begin.basicPoint == region.end.basicPoint)
                    base = ReferencePoint.encode(region.begin.getWithoutOffset(), true);
                
                if (base != null) {
                    if (region.hasOffsets())
                        base += "(" + region.begin.offset + ", " + region.end.offset + ")";
                    encodes[i] = base;
                    continue out;
                }
            }
            encodes[i] = "{" + ReferencePoint.encode(region.begin, true) + ":" + ReferencePoint.encode(region.end, false) + "}";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; ; i++) {
            sb.append(encodes[i]);
            if (i == encodes.length - 1)
                break;
            sb.append("+");
        }
        return sb.toString();
    }

    public static boolean match(ReferenceRange a, GeneFeature b) {
        if (b.isComposite())
            return false;
        return a.begin.basicPoint == b.regions[0].begin.basicPoint
                && a.end.basicPoint == b.regions[0].end.basicPoint;
    }

    public static class Deserializer extends JsonDeserializer<GeneFeature> {
        @Override
        public GeneFeature deserialize(JsonParser jp, DeserializationContext ctxt) throws IOException {
            return parse(jp.readValueAs(String.class));
        }

        @Override
        public GeneFeature getEmptyValue() {
            return null;
        }

        @Override
        public GeneFeature getNullValue() {
            return null;
        }
    }

    public static final class Serializer extends JsonSerializer<GeneFeature> {
        @Override
        public void serialize(GeneFeature value, JsonGenerator jgen, SerializerProvider provider) throws IOException, JsonProcessingException {
            String name = encode(value);
            if (name == null)
                throw new RuntimeException("Not yet supported.");
            jgen.writeString(name);
        }
    }
}
