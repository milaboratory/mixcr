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

import java.util.ArrayList;

/**
 * Enum of immunological genes. T and B cell receptors.
 *
 * @author Bolotin Dmitriy (bolotin.dmitriy@gmail.com)
 * @author Shugay Mikhail (mikhail.shugay@gmail.com)
 */
public enum Locus implements java.io.Serializable {
    TRA("TRA", "alpha", false), TRB("TRB", "beta", true),
    TRG("TRG", "gamma", false), TRD("TRD", "delta", true),
    IGL("IGL", "lambda", false), IGK("IGK", "kappa", false),
    IGH("IGH", "heavy", true);

    final String id, greekLetter;
    final boolean ig;
    final boolean hasDGene;

    Locus(String id, String greekLetter, boolean hasDGene) {
        this.id = id;
        this.hasDGene = hasDGene;
        this.greekLetter = greekLetter;
        this.ig = (id.charAt(0) == 'I');
    }

    public boolean hasDSegment() {
        return hasDGene;
    }

    public boolean isIg() {
        return ig;
    }

    public String getId() {
        return id;
    }

    public String getGreekLetter() {
        return greekLetter;
    }

    public static Locus fromId(String id) {
        for (Locus g : values())
            if (id.equalsIgnoreCase(g.id))
                return g;
        return null;
    }

    public static Locus fromIdSafe(String id) {
        for (Locus g : values())
            if (id.equalsIgnoreCase(g.id))
                return g;
        throw new IllegalArgumentException("Unknown locus:" + id);
    }

    @Override
    public String toString() {
        return id;
    }

    static final Locus[] lT, lIG;

    static {
        ArrayList<Locus> ig = new ArrayList<>(), t = new ArrayList<>();
        for (Locus l : values())
            if (l.isIg())
                ig.add(l);
            else t.add(l);
        lT = t.toArray(new Locus[t.size()]);
        lIG = ig.toArray(new Locus[t.size()]);
    }

    public static Locus[] getAllTCRLoci() {
        return lT.clone();
    }

    public static Locus[] getAllBCRLoci() {
        return lIG.clone();
    }
}
