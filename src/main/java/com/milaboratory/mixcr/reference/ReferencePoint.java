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

import com.milaboratory.primitivio.annotations.Serializable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a {@link BasicReferencePoint} with offset.
 *
 * @see GeneFeature
 */
@Serializable(by = IO.ReferencePointSerializer.class)
public final class ReferencePoint implements Comparable<ReferencePoint>, java.io.Serializable {

    /* V */

    /**
     * Beginning of IG/TCR transcript
     */
    public static final ReferencePoint UTR5Begin = new ReferencePoint(BasicReferencePoint.V5UTRBegin),
    /**
     * End of 5'UTR, beginning of IG/TCR CDS as listed in database
     */
    V5UTREnd = new ReferencePoint(BasicReferencePoint.V5UTREndL1Begin),
    /**
     * End of 5'UTR, beginning of IG/TCR CDS as observed in the data
     */
    V5UTRBeginTrimmed = new ReferencePoint(BasicReferencePoint.V5UTRBeginTrimmed),
    /**
     * End of 5'UTR, beginning of IG/TCR CDS
     */
    L1Begin = new ReferencePoint(BasicReferencePoint.V5UTREndL1Begin),
    /**
     * End of first exon, beginning of V intron
     */
    L1End = new ReferencePoint(BasicReferencePoint.L1EndVIntronBegin),
    /**
     * End of first exon, beginning of V intron
     */
    VIntronBegin = new ReferencePoint(BasicReferencePoint.L1EndVIntronBegin),
    /**
     * End of V intron, beginning of second exon
     */
    VIntronEnd = new ReferencePoint(BasicReferencePoint.VIntronEndL2Begin),
    /**
     * End of V intron, beginning of second exon
     */
    L2Begin = new ReferencePoint(BasicReferencePoint.VIntronEndL2Begin),
    /**
     * End of lider sequence, beginning of sequence that codes IG/TCR protein, beginning of FR1.
     */
    L2End = new ReferencePoint(BasicReferencePoint.L2EndFR1Begin),
    /**
     * End of lider sequence, beginning of sequence that codes IG/TCR protein, beginning of FR1.
     */
    FR1Begin = new ReferencePoint(BasicReferencePoint.L2EndFR1Begin),
    /**
     * End of FR1, beginning of CDR1
     */
    FR1End = new ReferencePoint(BasicReferencePoint.FR1EndCDR1Begin),
    /**
     * End of FR1, beginning of CDR1
     */
    CDR1Begin = new ReferencePoint(BasicReferencePoint.FR1EndCDR1Begin),
    /**
     * End of CDR1, beginning of FR2
     */
    CDR1End = new ReferencePoint(BasicReferencePoint.CDR1EndFR2Begin),
    /**
     * End of CDR1, beginning of FR2
     */
    FR2Begin = new ReferencePoint(BasicReferencePoint.CDR1EndFR2Begin),
    /**
     * End of FR2, beginning of CDR2
     */
    FR2End = new ReferencePoint(BasicReferencePoint.FR2EndCDR2Begin),
    /**
     * End of FR2, beginning of CDR2
     */
    CDR2Begin = new ReferencePoint(BasicReferencePoint.FR2EndCDR2Begin),
    /**
     * End of CDR2, beginning of FR3
     */
    CDR2End = new ReferencePoint(BasicReferencePoint.CDR2EndFR3Begin),
    /**
     * End of CDR2, beginning of FR3
     */
    FR3Begin = new ReferencePoint(BasicReferencePoint.CDR2EndFR3Begin),
    /**
     * End of FR3, beginning of CDR3
     */
    FR3End = new ReferencePoint(BasicReferencePoint.FR3EndCDR3Begin),
    /**
     * End of FR3, beginning of CDR3
     */
    CDR3Begin = new ReferencePoint(BasicReferencePoint.FR3EndCDR3Begin),
    /**
     * End of V region after V(D)J rearrangement (commonly inside CDR3)
     */
    VEndTrimmed = new ReferencePoint(BasicReferencePoint.VEndTrimmed),
    /**
     * End of V region in genome
     */
    VEnd = new ReferencePoint(BasicReferencePoint.VEnd),

    /* D */

    /**
     * Beginning of D region in genome
     */
    DBegin = new ReferencePoint(BasicReferencePoint.DBegin),
    /**
     * Beginning of D region after VDJ rearrangement
     */
    DBeginTrimmed = new ReferencePoint(BasicReferencePoint.DBeginTrimmed),
    /**
     * End of D region after VDJ rearrangement
     */
    DEndTrimmed = new ReferencePoint(BasicReferencePoint.DEndTrimmed),
    /**
     * End of D region in genome
     */
    DEnd = new ReferencePoint(BasicReferencePoint.DEnd),

    /* J */

    /**
     * Beginning of J region in genome
     */
    JBegin = new ReferencePoint(BasicReferencePoint.JBegin),
    /**
     * Beginning of J region after V(D)J rearrangement
     */
    JBeginTrimmed = new ReferencePoint(BasicReferencePoint.JBeginTrimmed),
    /**
     * End of CDR3, beginning of FR4
     */
    CDR3End = new ReferencePoint(BasicReferencePoint.CDR3EndFR4Begin),
    /**
     * End of CDR3, beginning of FR4
     */
    FR4Begin = new ReferencePoint(BasicReferencePoint.CDR3EndFR4Begin),
    /**
     * End of FR4
     */
    FR4End = new ReferencePoint(BasicReferencePoint.FR4End),

    /* C */

    /**
     * Beginning of C Region
     */
    CBegin = new ReferencePoint(BasicReferencePoint.CBegin),
    /**
     * End of C Region first exon (Exon 3 of assembled TCR/IG gene)
     */
    CExon1End = new ReferencePoint(BasicReferencePoint.CExon1End),
    /**
     * End of C Region
     */
    CEnd = new ReferencePoint(BasicReferencePoint.CEnd);

    /**
     * Default set of reference points.
     */
    public static final ReferencePoint[] DefaultReferencePoints = {V5UTRBeginTrimmed, L1Begin, L1End, L2Begin,
            FR1Begin, CDR1Begin, FR2Begin, CDR2Begin, FR3Begin, CDR3Begin, VEndTrimmed, DBeginTrimmed, DEndTrimmed,
            JBeginTrimmed, FR4Begin, FR4End, CBegin, CExon1End};

    static final long serialVersionUID = 1L;
    final BasicReferencePoint basicPoint;
    final int offset;

    /**
     * Creates generalized reference point that represents pure reference point.
     *
     * @param basicPoint reference point
     */
    ReferencePoint(BasicReferencePoint basicPoint) {
        this(basicPoint, 0);
    }

    /**
     * Creates new generalized reference point.
     *
     * @param basicPoint reference point
     * @param offset     offset
     */
    ReferencePoint(BasicReferencePoint basicPoint, int offset) {
        if (basicPoint == null)
            throw new NullPointerException();

        this.basicPoint = basicPoint;
        this.offset = offset;
    }

    /**
     * Creates generalized reference point that represents pure reference point.
     *
     * @param referencePoint reference point
     * @param offset         offset
     */
    public ReferencePoint(ReferencePoint referencePoint, int offset) {
        if (referencePoint == null)
            throw new NullPointerException();

        this.basicPoint = referencePoint.basicPoint;
        this.offset = offset;
    }

    /**
     * Returns true if offset is equals to zero, so this object represents pure reference point.
     *
     * @return true if offset is equals to zero
     */
    public boolean hasNoOffset() {
        return offset == 0;
    }

    public ReferencePoint move(int offset) {
        return new ReferencePoint(basicPoint, offset + this.offset);
    }

    /**
     * Returns offset. May be negative.
     *
     * @return offset
     */
    public int getOffset() {
        return offset;
    }

    public boolean sameOrigin(ReferencePoint referencePoint) {
        return basicPoint == referencePoint.basicPoint;
    }

    public int getOffsetFrom(ReferencePoint other) {
        if (!sameOrigin(other))
            throw new IllegalArgumentException("Points with different origin.");
        return offset - other.offset;
    }

    public boolean isTrimmable() {
        return basicPoint.isTrimmable();
    }

    int getIndex() {
        return basicPoint.index;
    }

    public GeneType getGeneType() {
        return basicPoint.geneType;
    }

    public boolean isAttachedToAlignmentBound() {
        return basicPoint.isAttachedToAlignmentBound();
    }

    public boolean isAttachedToLeftAlignmentBound() {
        return basicPoint.isAttachedToLeftAlignmentBound();
    }

    public ReferencePoint getWithoutOffset() {
        if (offset == 0)
            return this;
        return new ReferencePoint(basicPoint);
    }

    public ReferencePoint getActivationPoint() {
        return basicPoint.getActivationPoint();
    }

    @Override
    public int compareTo(ReferencePoint o) {
        int c = basicPoint.compareTo(o.basicPoint);
        if (c != 0)
            return c;
        return Integer.compare(offset, o.getOffset());
    }

    @Override
    public String toString() {
        return "" + basicPoint +
                (offset != 0 ?
                        (offset > 0 ? "+" : "") + offset :
                        "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReferencePoint)) return false;

        ReferencePoint that = (ReferencePoint) o;

        if (offset != that.offset) return false;
        return basicPoint == that.basicPoint;
    }

    @Override
    public int hashCode() {
        int result = basicPoint.hashCode();
        result = 31 * result + offset;
        return result;
    }


    private static Map<String, ReferencePoint> pointByName = null;
    private static Map<ReferencePoint, List<String>> nameByPoint = null;

    private static void ensureInitialized() {
        if (pointByName == null) {
            synchronized (GeneFeature.class) {
                if (pointByName == null) {
                    try {
                        Map<String, ReferencePoint> fbn = new HashMap<>();
                        Map<ReferencePoint, List<String>> nbf = new HashMap<>();
                        Field[] declaredFields = ReferencePoint.class.getDeclaredFields();
                        for (Field field : declaredFields)
                            if (Modifier.isStatic(field.getModifiers()) &&
                                    field.getType() == ReferencePoint.class) {
                                ReferencePoint value = (ReferencePoint) field.get(null);
                                String name = field.getName();
                                fbn.put(name.toLowerCase(), value);
                                List<String> l = nbf.get(value);
                                if (l == null) {
                                    l = new ArrayList<>();
                                    nbf.put(value, l);
                                }
                                l.add(name);
                            }
                        pointByName = fbn;
                        nameByPoint = nbf;
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
    }

    public static ReferencePoint getPointByName(String pointName) {
        ensureInitialized();
        return pointByName.get(pointName.toLowerCase());
    }

    public static String getNameByPoint(ReferencePoint point) {
        ensureInitialized();
        return nameByPoint.get(point).get(0);
    }

    public static ReferencePoint parse(String string) {
        string = string.trim();
        int br = string.indexOf('(');
        ReferencePoint base;
        if (br == -1)
            base = getPointByName(string);
        else
            base = getPointByName(string.substring(0, br));
        if (base == null)
            throw new IllegalArgumentException("Unknown point: " + string);

        if (br == -1) return base;

        int offset;
        try {
            offset = Integer.parseInt(string.substring(br + 1, string.length() - 1).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Incorrect input: " + string);
        }
        return new ReferencePoint(base, offset);
    }

    public static String encode(ReferencePoint point, boolean begin) {
        ensureInitialized();
        List<String> names = nameByPoint.get(point.getWithoutOffset());
        String match = begin ? "Begin" : "End", name = null;
        for (String n : names)
            if (n.contains(match)) {
                name = n;
                break;
            }
        if (name == null)
            if (names.size() == 1)
                name = names.get(0);
            else
                throw new RuntimeException();

        if (point.offset == 0)
            return name;
        return name + "(" + point.offset + ")";
    }
}
