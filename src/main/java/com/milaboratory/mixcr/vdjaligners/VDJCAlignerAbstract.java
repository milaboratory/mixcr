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

import cc.redberry.primitives.Filter;
import com.milaboratory.core.alignment.batch.AlignmentHit;
import com.milaboratory.core.alignment.batch.BatchAlignerWithBaseParameters;
import com.milaboratory.core.alignment.batch.BatchAlignerWithBaseWithFilter;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.HasGene;
import com.milaboratory.util.BitArray;
import io.repseq.core.Chains;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;

public abstract class VDJCAlignerAbstract<R extends SequenceRead> extends VDJCAligner<R> {
    protected volatile SingleDAligner singleDAligner = null;
    /**
     * Filter geneType -> (chain -> [corresponding gene indexes])
     */
    protected volatile EnumMap<GeneType, HashMap<String, BitArray>> filters;
    protected volatile BatchAlignerWithBaseWithFilter<NucleotideSequence, VDJCGene, AlignmentHit<NucleotideSequence, VDJCGene>> vAligner = null;
    protected volatile BatchAlignerWithBaseWithFilter<NucleotideSequence, VDJCGene, AlignmentHit<NucleotideSequence, VDJCGene>> jAligner = null;
    protected volatile BatchAlignerWithBaseWithFilter<NucleotideSequence, VDJCGene, AlignmentHit<NucleotideSequence, VDJCGene>> cAligner = null;

    public VDJCAlignerAbstract(VDJCAlignerParameters parameters) {
        super(parameters);
    }

    VDJCAlignerAbstract(boolean initialized,
                               VDJCAlignerParameters parameters,
                               EnumMap<GeneType, List<VDJCGene>> genesToAlign,
                               List<VDJCGene> usedGenes,
                               SingleDAligner singleDAligner,
                               EnumMap<GeneType, HashMap<String, BitArray>> filters,
                               BatchAlignerWithBaseWithFilter<NucleotideSequence, VDJCGene, AlignmentHit<NucleotideSequence, VDJCGene>> vAligner,
                               BatchAlignerWithBaseWithFilter<NucleotideSequence, VDJCGene, AlignmentHit<NucleotideSequence, VDJCGene>> jAligner,
                               BatchAlignerWithBaseWithFilter<NucleotideSequence, VDJCGene, AlignmentHit<NucleotideSequence, VDJCGene>> cAligner) {
        super(initialized, parameters, genesToAlign, usedGenes);
        this.singleDAligner = singleDAligner;
        this.filters = filters;
        this.vAligner = vAligner;
        this.jAligner = jAligner;
        this.cAligner = cAligner;
    }

    @SuppressWarnings("unchecked")
    private BatchAlignerWithBaseWithFilter<NucleotideSequence, VDJCGene, AlignmentHit<NucleotideSequence, VDJCGene>> createKAligner(GeneType geneType) {
        if (parameters.getVJCGeneAlignerParameters(geneType) != null &&
                !genesToAlign.get(geneType).isEmpty()) {
            BatchAlignerWithBaseWithFilter<NucleotideSequence, VDJCGene, AlignmentHit<NucleotideSequence, VDJCGene>> aligner =
                    (BatchAlignerWithBaseWithFilter) extractBatchParameters(parameters.getVJCGeneAlignerParameters(geneType)).createAligner();
            for (VDJCGene a : genesToAlign.get(geneType))
                aligner.addReference(a.getFeature(parameters.getVJCGeneAlignerParameters(geneType).getGeneFeatureToAlign()), a);
            return aligner;
        }
        return null;
    }

    protected BatchAlignerWithBaseParameters extractBatchParameters(KGeneAlignmentParameters init) {
        return init.getParameters();
    }

    protected final BatchAlignerWithBaseWithFilter<NucleotideSequence, VDJCGene,
            AlignmentHit<NucleotideSequence, VDJCGene>> getAligner(GeneType type) {
        switch (type) {
            case Variable:
                return vAligner;
            case Joining:
                return jAligner;
            case Constant:
                return cAligner;
        }
        return null;
    }

    protected BitArray getFilter(GeneType targetAlignerType,
                                 List<? extends AlignmentHit<?, ? extends VDJCGene>> result) {
        if (parameters.isAllowChimeras() || result == null || result.isEmpty())
            return null;

        Chains c = result.get(0).getRecordPayload().getChains();
        for (int i = 1; i < result.size(); i++)
            c = c.merge(result.get(i).getRecordPayload().getChains());

        return getFilter0(targetAlignerType, c);
    }

    protected BitArray getFilter(GeneType targetAlignerType,
                                 List<? extends AlignmentHit<?, ? extends VDJCGene>> result1,
                                 List<? extends AlignmentHit<?, ? extends VDJCGene>> result2) {
        if (parameters.isAllowChimeras())
            return null;

        return mergeFilters(
                getFilter(targetAlignerType, result1),
                getFilter(targetAlignerType, result2));
    }

    protected BitArray getFilter(GeneType targetAlignerType, HasGene[] hits) {
        if (parameters.isAllowChimeras() || hits == null || hits.length == 0)
            return null;
        Chains c = hits[0].getGene().getChains();
        for (int i = 1; i < hits.length; i++)
            c = c.merge(hits[i].getGene().getChains());
        return getFilter0(targetAlignerType, c);
    }

    protected BitArray getFilter(GeneType targetAlignerType, HasGene[] hits1, HasGene[] hits2) {
        if (parameters.isAllowChimeras())
            return null;

        return mergeFilters(
                getFilter(targetAlignerType, hits1),
                getFilter(targetAlignerType, hits2));
    }

    protected BitArray mergeFilters(BitArray filter1, BitArray filter2) {
        if (filter1 == null)
            return filter2;
        if (filter2 == null)
            return filter1;
        filter1 = filter1.clone();
        filter1.or(filter2);
        return filter1;
    }

    protected BitArray getFilter(GeneType targetAlignerType, Chains chains) {
        if (parameters.isAllowChimeras() || chains.equals(Chains.ALL))
            return null;
        return getFilter0(targetAlignerType, chains);
    }

    private BitArray getFilter0(GeneType targetAlignerType, Chains chains) {
        BitArray ret = null;
        boolean cloned = false;
        for (String chain : chains)
            if (ret == null)
                ret = filters.get(targetAlignerType).get(chain);
            else {
                if (!cloned) {
                    ret = ret.clone();
                    cloned = true;
                }
                ret.or(filters.get(targetAlignerType).get(chain));
            }
        return ret;
    }

    @Override
    protected void init() {
        DAlignerParameters dAlignerParameters = parameters.getDAlignerParameters();
        List<VDJCGene> dGenes = genesToAlign.get(GeneType.Diversity);
        if (dAlignerParameters != null && dGenes.size() != 0)
            singleDAligner = new SingleDAligner(dAlignerParameters,
                    genesToAlign.get(GeneType.Diversity));
        vAligner = createKAligner(GeneType.Variable);
        jAligner = createKAligner(GeneType.Joining);
        cAligner = createKAligner(GeneType.Constant);

        Chains chains = new Chains();
        for (VDJCGene gene : getUsedGenes())
            chains = chains.merge(gene.getChains());

        filters = new EnumMap<>(GeneType.class);

        for (GeneType geneType : GeneType.VJC_REFERENCE) {
            HashMap<String, BitArray> f = new HashMap<>();
            for (final String chain : chains) {
                BatchAlignerWithBaseWithFilter<NucleotideSequence, VDJCGene, AlignmentHit<NucleotideSequence, VDJCGene>> aligner = getAligner(geneType);
                if (aligner != null)
                    f.put(chain, aligner.createFilter(new Filter<VDJCGene>() {
                        @Override
                        public boolean accept(VDJCGene object) {
                            return object.getChains().contains(chain);
                        }
                    }));
            }
            filters.put(geneType, f);
        }
    }
}
