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

@Serializable(by = IO.SpeciesAndLocusSerializer.class)
public final class SpeciesAndLocus implements Comparable<SpeciesAndLocus> {
    //static final long serialVersionUID = 1L;
    public final int taxonId;
    public final Locus locus;

    public SpeciesAndLocus(int taxonId, Locus locus) {
        this.taxonId = taxonId;
        this.locus = locus;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SpeciesAndLocus that = (SpeciesAndLocus) o;

        if (locus != that.locus) return false;

        return taxonId == taxonId;
    }

    @Override
    public int hashCode() {
        int result = taxonId;
        result = 31 * result + locus.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "" + taxonId + ":" + locus;
    }

    @Override
    public int compareTo(SpeciesAndLocus o) {
        int r;
        if ((r = locus.compareTo(o.locus)) != 0)
            return r;
        return Integer.compare(taxonId, o.taxonId);
    }
}
