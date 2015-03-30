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

/**
 * Group type of a segment.
 *
 * @author Bolotin Dmitriy (bolotin.dmitriy@gmail.com)
 * @author Shugay Mikhail (mikhail.shugay@gmail.com)
 */
public enum GeneType {
    Variable((byte) 0, 'V', +1, 11),
    Diversity((byte) 2, 'D', 0, 2),
    Joining((byte) 1, 'J', -1, 3),
    Constant((byte) 3, 'C', -2, 3);
    private final byte id;
    private final char letter;
    private final int cdr3Side;
    private final int completeNumberOfReferencePoints;

    private GeneType(byte id, char letter, int cdr3Side, int completeNumberOfReferencePoints) {
        this.id = id;
        this.letter = letter;
        this.cdr3Side = cdr3Side;
        this.completeNumberOfReferencePoints = completeNumberOfReferencePoints;
    }

    public static GeneType fromChar(char letter) {
        switch (letter) {
            case 'C':
            case 'c':
                return Constant;
            case 'V':
            case 'v':
                return Variable;
            case 'J':
            case 'j':
                return Joining;
            case 'D':
            case 'd':
                return Diversity;
        }
        throw new IllegalArgumentException("Unrecognized GeneType letter.");
    }

    /**
     * Gets a segment by id
     *
     * @param id
     */
    public static GeneType get(int id) {
        for (GeneType st : values())
            if (st.id == id)
                return st;
        throw new RuntimeException("Unknown ID");
    }

    /**
     * Gets the associated letter, e.g. V for TRBV
     */
    public char getLetter() {
        return letter;
    }

    /**
     * Id of segment
     */
    public byte id() {
        return id;
    }

    /**
     * Gets an integer indicating position of segment of this type relative to CDR3
     *
     * @return +1 (upstream of CDR3, V gene), 0 (inside CDR3, D gene), -1 (downstream of CDR3, J gene) and -2
     * (downstream of CDR3, C segment)
     */
    public int cdr3Site() {
        return cdr3Side;
    }

    public int getCompleteNumberOfReferencePoints() {
        return completeNumberOfReferencePoints;
    }

    public static final int NUMBER_OF_TYPES;

    static {
        NUMBER_OF_TYPES = values().length;
    }
}
