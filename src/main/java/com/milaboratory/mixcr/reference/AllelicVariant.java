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
import com.milaboratory.core.mutations.Mutations;
import com.milaboratory.core.sequence.NucleotideSequence;

public class AllelicVariant extends Allele {
    final GeneFeature referenceGeneFeature;
    final ReferenceAllele referenceAllele;
    final Mutations<NucleotideSequence> mutations;
    final ReferencePoints referencePoints;
    final NucleotideSequence sequence;

    public AllelicVariant(String name, boolean isFunctional,
                          GeneFeature referenceGeneFeature, ReferenceAllele referenceAllele,
                          Mutations<NucleotideSequence> mutations) {
        super(referenceAllele.getGene(), name, isFunctional);
        this.referenceGeneFeature = referenceGeneFeature;
        this.referenceAllele = referenceAllele;
        this.sequence = mutations.mutate(referenceAllele.getFeature(referenceGeneFeature));
        this.mutations = mutations;
        this.referencePoints =
                referenceAllele.getPartitioning().getRelativeReferencePoints(referenceGeneFeature)
                        .applyMutations(mutations);
    }

    @Override
    public boolean isReference() {
        return false;
    }

    @Override
    public ReferencePoints getPartitioning() {
        return referencePoints;
    }

    @Override
    protected NucleotideSequence getSequence(Range range) {
        return sequence.getRange(range);
    }

    public ReferenceAllele getReferenceAllele() {
        return referenceAllele;
    }

    public Mutations<NucleotideSequence> getMutations() {
        return mutations;
    }
}
