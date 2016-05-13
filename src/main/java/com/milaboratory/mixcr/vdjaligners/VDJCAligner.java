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

import cc.redberry.pipe.Processor;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import io.repseq.reference.Allele;
import io.repseq.reference.GeneType;
import io.repseq.reference.Locus;

import java.util.*;

public abstract class VDJCAligner<R extends SequenceRead> implements Processor<R, VDJCAlignmentResult<R>> {
    protected volatile boolean initialized = false;
    protected final VDJCAlignerParameters parameters;
    protected final EnumMap<GeneType, List<Allele>> allelesToAlign = new EnumMap<>(GeneType.class);
    protected final List<Allele> usedAlleles = new ArrayList<>();
    protected VDJCAlignerEventListener listener = null;

    protected VDJCAligner(VDJCAlignerParameters parameters) {
        this.parameters = parameters.clone();
        for (GeneType geneType : GeneType.values())
            allelesToAlign.put(geneType, new ArrayList<Allele>());
    }

    public void setEventsListener(VDJCAlignerEventListener listener) {
        this.listener = listener;
    }

    protected final void onFailedAlignment(SequenceRead read, VDJCAlignmentFailCause cause) {
        if (listener != null)
            listener.onFailedAlignment(read, cause);
    }

    protected final void onSuccessfulAlignment(SequenceRead read, VDJCAlignments alignment) {
        if (listener != null)
            listener.onSuccessfulAlignment(read, alignment);
    }

    public boolean isInitialized() {
        return initialized;
    }

    public final void ensureInitialized() {
        if (!initialized)
            synchronized (this) {
                if (!initialized) {
                    init();
                    initialized = true;
                }
            }
    }

    protected abstract void init();

    public VDJCAlignerParameters getParameters() {
        return parameters.clone();
    }

    public List<Allele> getUsedAlleles() {
        return Collections.unmodifiableList(usedAlleles);
    }

    public int addAllele(Allele allele) {
        usedAlleles.add(allele);
        List<Allele> alleles = allelesToAlign.get(allele.getGeneType());
        alleles.add(allele);
        return alleles.size() - 1;
    }

    public Allele getAllele(GeneType type, int index) {
        return allelesToAlign.get(type).get(index);
    }

    public List<Allele> getVAllelesToAlign() {
        return allelesToAlign.get(GeneType.Variable);
    }

    public List<Allele> getDAllelesToAlign() {
        return allelesToAlign.get(GeneType.Diversity);
    }

    public List<Allele> getJAllelesToAlign() {
        return allelesToAlign.get(GeneType.Joining);
    }

    public List<Allele> getCAllelesToAlign() {
        return allelesToAlign.get(GeneType.Constant);
    }

    public static VDJCAligner createAligner(VDJCAlignerParameters alignerParameters,
                                            boolean paired, boolean merge) {
        return paired ?
                merge ? new VDJCAlignerWithMerge(alignerParameters)
                        : new VDJCAlignerPVFirst(alignerParameters)
                : new VDJCAlignerSJFirst(alignerParameters);
    }

    public static Set<Locus> getPossibleDLoci(VDJCHit[] vHits, VDJCHit[] jHits) {
        EnumSet loci = EnumSet.noneOf(Locus.class);
        for (VDJCHit h : vHits)
            loci.add(h.getAllele().getLocus());
        for (VDJCHit h : jHits)
            loci.add(h.getAllele().getLocus());
        return loci;
    }
}
