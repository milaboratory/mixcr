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

import com.milaboratory.core.Range;
import com.milaboratory.core.sequence.NucleotideSequence;

public final class ReferenceAllele extends Allele {
    final String accession;
    final ReferencePoints referencePoints;

    public ReferenceAllele(Gene gene, String name, boolean isFunctional,
                           String accession, ReferencePoints referencePoints) {
        super(gene, name, isFunctional);
        this.accession = accession;
        this.referencePoints = referencePoints;
    }

    @Override
    public boolean isReference() {
        return true;
    }

    @Override
    protected NucleotideSequence getSequence(Range range) {
        return locusContainer.getLibrary().getBase().get(accession, range);
    }

    @Override
    public ReferencePoints getPartitioning() {
        return referencePoints;
    }

    public String getAccession() {
        return accession;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        ReferenceAllele that = (ReferenceAllele) o;

        if (accession != null ? !accession.equals(that.accession) : that.accession != null) return false;
        if (referencePoints != null ? !referencePoints.equals(that.referencePoints) : that.referencePoints != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = accession != null ? accession.hashCode() : 0;
        result = 31 * result + (referencePoints != null ? referencePoints.hashCode() : 0);
        return result;
    }
}
