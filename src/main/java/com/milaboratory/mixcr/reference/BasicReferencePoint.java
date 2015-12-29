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


import static com.milaboratory.mixcr.reference.GeneType.*;

enum BasicReferencePoint implements java.io.Serializable {
    // Points in V
    V5UTRBegin(0, GeneType.Variable, 0),
    V5UTRBeginTrimmed(-1, GeneType.Variable, 1, "V5UTREnd"),
    V5UTREndL1Begin(1, Variable, 2),
    L1EndVIntronBegin(2, Variable, 3),
    VIntronEndL2Begin(3, Variable, 4),
    L2EndFR1Begin(4, Variable, 5),
    FR1EndCDR1Begin(5, Variable, 6),
    CDR1EndFR2Begin(6, Variable, 7),
    FR2EndCDR2Begin(7, Variable, 8),
    CDR2EndFR3Begin(8, Variable, 9),
    FR3EndCDR3Begin(9, Variable, 10),
    VEndTrimmed(-2, Variable, 11, "CDR3Begin(-3)"),
    VEnd(10, Variable, 12),

    // Points in D
    DBegin(11, Diversity, 13),
    DBeginTrimmed(-1, Diversity, 14, null),
    DEndTrimmed(-2, Diversity, 15, null),
    DEnd(12, Diversity, 16),

    // Points in J
    JBegin(13, Joining, 17),
    JBeginTrimmed(-1, Joining, 18, "CDR3End(+3)"),
    CDR3EndFR4Begin(14, Joining, 19),
    FR4End(15, Joining, 20),

    // Points in C
    CBegin(16, Constant, 21),
    CExon1End(17, Constant, 22),
    CEnd(18, Constant, 23);

    final int orderingIndex;
    final int index;
    final GeneType geneType;
    BasicReferencePoint trimmedVersion;

    /* Only for trimmed (attached to alignment boundary) points */

    // Defined for alignment boundary attached reference points
    // E.g. V5UTRBeginTrimmed is a left boundary of an alignment but only if it is on the left side of V5UTREnd/L1Begin
    // Not an object to solve cyclic dependence on ReferencePoint
    final String activationPointString;
    volatile ReferencePoint activationPoint;


    BasicReferencePoint(int index, GeneType geneType, int orderingIndex) {
        this(index, geneType, orderingIndex, null);
    }

    BasicReferencePoint(int index, GeneType geneType, int orderingIndex, String activationPoint) {
        this.activationPointString = activationPoint;
        this.index = index;
        this.geneType = geneType;
        this.orderingIndex = orderingIndex;
    }

    public static BasicReferencePoint getByIndex(int index) {
        return allReferencePoints[index];
    }

    public boolean isAttachedToAlignmentBound() {
        return index < 0;
    }

    /**
     * Returns true if this point is nor attached to any alignments bound.
     *
     * <p>Information about this point is taken from reference sequence's reference points.</p>
     *
     * @return true if this point is nor attached to any alignments bound
     */
    public boolean isPure() {
        return index >= 0;
    }

    public boolean isAttachedToLeftAlignmentBound() {
        assert index < 0;
        return index == -1;
    }

    public boolean isTrimmable() {
        return trimmedVersion != null;
    }

    public ReferencePoint getActivationPoint() {
        if (activationPointString == null)
            return null;

        if (activationPoint == null) {
            synchronized (this) {
                if (activationPoint == null)
                    return activationPoint = ReferencePoint.parse(activationPointString);
            }
        }

        return activationPoint;
    }

    private final static BasicReferencePoint[] allReferencePoints;
    public static final int TOTAL_NUMBER_OF_REFERENCE_POINTS = 19;

    static {
        allReferencePoints = new BasicReferencePoint[TOTAL_NUMBER_OF_REFERENCE_POINTS];

        for (BasicReferencePoint rp : values()) {
            if (rp.isAttachedToAlignmentBound())
                continue;
            assert allReferencePoints[rp.index] == null;
            allReferencePoints[rp.index] = rp;
        }

        for (BasicReferencePoint rp : allReferencePoints)
            assert rp != null;

        V5UTRBegin.trimmedVersion = V5UTRBeginTrimmed;
        VEnd.trimmedVersion = VEndTrimmed;
        DBegin.trimmedVersion = DBeginTrimmed;
        DEnd.trimmedVersion = DEndTrimmed;
        JBegin.trimmedVersion = JBeginTrimmed;
    }
}
