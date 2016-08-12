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


import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.PartitionedSequenceCached;
import com.milaboratory.primitivio.annotations.Serializable;

@Serializable(by = IO.AlleleSerializer.class)
public abstract class Allele
        extends PartitionedSequenceCached<NucleotideSequence>
        implements Comparable<Allele> {
    final Gene gene;
    final LocusContainer locusContainer;
    final int taxonId;
    final String name;
    final boolean isFunctional;
    final AlleleId alleleId;

    protected Allele(Gene gene, String name, boolean isFunctional) {
        this.gene = gene;
        this.locusContainer = gene.locusContainer;
        this.taxonId = locusContainer.getSpeciesAndLocus().taxonId;
        this.name = name;
        this.isFunctional = isFunctional;
        this.alleleId = new AlleleId(getLocusContainer().getUUID(), getLocusContainer().getSpeciesAndLocus(), name);
    }

    public final boolean isComplete() {
        return gene.getGroup().getType().getCompleteNumberOfReferencePoints() == getPartitioning().numberOfDefinedPoints();
    }

    public final LocusContainer getLocusContainer() {
        return locusContainer;
    }

    public final AlleleId getId() {
        return alleleId;
    }

    public final int getTaxonId() {
        return taxonId;
    }

    public final Gene getGene() {
        return gene;
    }

    public final String getName() {
        return name;
    }

    public final String getFamilyName() {
        return name.split("-")[0].split("\\*")[0];
    }

    public final GeneGroup getGeneGroup() {
        return gene.getGroup();
    }

    public final GeneType getGeneType() {
        return getGeneGroup().getType();
    }

    public final Locus getLocus() {
        return getGeneGroup().getLocus();
    }

    public final boolean isFunctional() {
        return isFunctional;
    }

    public abstract boolean isReference();

    @Override
    public abstract ReferencePoints getPartitioning();

    @Override
    public int compareTo(Allele o) {
        return alleleId.compareTo(o.alleleId);
    }
}
