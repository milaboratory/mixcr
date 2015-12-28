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
package com.milaboratory.mixcr.vdjaligners;

import com.milaboratory.core.alignment.batch.AlignmentHit;
import com.milaboratory.core.alignment.batch.BatchAlignerWithBase;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.reference.Allele;
import com.milaboratory.mixcr.reference.GeneType;

import java.util.List;

public abstract class VDJCAlignerAbstract<R extends SequenceRead> extends VDJCAligner<R> {
    protected volatile SingleDAligner singleDAligner = null;
    protected volatile BatchAlignerWithBase<NucleotideSequence, Allele, AlignmentHit<NucleotideSequence, Allele>> vAligner = null;
    protected volatile BatchAlignerWithBase<NucleotideSequence, Allele, AlignmentHit<NucleotideSequence, Allele>> jAligner = null;
    protected volatile BatchAlignerWithBase<NucleotideSequence, Allele, AlignmentHit<NucleotideSequence, Allele>> cAligner = null;

    public VDJCAlignerAbstract(VDJCAlignerParameters parameters) {
        super(parameters);
    }

    @SuppressWarnings("unchecked")
    private BatchAlignerWithBase<NucleotideSequence, Allele, AlignmentHit<NucleotideSequence, Allele>> createKAligner(GeneType geneType) {
        if (parameters.getVJCGeneAlignerParameters(geneType) != null &&
                !allelesToAlign.get(geneType).isEmpty()) {
            BatchAlignerWithBase<NucleotideSequence, Allele, AlignmentHit<NucleotideSequence, Allele>> aligner =
                    (BatchAlignerWithBase) parameters.getVJCGeneAlignerParameters(geneType).getParameters().createAligner();
            for (Allele a : allelesToAlign.get(geneType))
                aligner.addReference(a.getFeature(parameters.getVJCGeneAlignerParameters(geneType).getGeneFeatureToAlign()), a);
            return aligner;
        }
        return null;
    }

    @Override
    protected void init() {
        DAlignerParameters dAlignerParameters = parameters.getDAlignerParameters();
        List<Allele> dAlleles = allelesToAlign.get(GeneType.Diversity);
        if (dAlignerParameters != null && dAlleles.size() != 0)
            singleDAligner = new SingleDAligner(dAlignerParameters,
                    allelesToAlign.get(GeneType.Diversity));
        vAligner = createKAligner(GeneType.Variable);
        jAligner = createKAligner(GeneType.Joining);
        cAligner = createKAligner(GeneType.Constant);
    }
}
