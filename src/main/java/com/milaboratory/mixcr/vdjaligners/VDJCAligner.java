/*
 * Copyright (c) 2014-2022, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.vdjaligners;

import cc.redberry.pipe.Processor;
import com.milaboratory.core.alignment.batch.AlignmentHit;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.io.sequence.SingleRead;
import com.milaboratory.mixcr.basictypes.HasGene;
import com.milaboratory.mixcr.basictypes.MiXCRMetaInfo;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.util.HashFunctions;
import com.milaboratory.util.RandomUtil;
import io.repseq.core.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

public abstract class VDJCAligner<R extends SequenceRead> implements Processor<R, VDJCAlignmentResult<R>> {
    protected volatile boolean initialized = false;
    protected final VDJCAlignerParameters parameters;
    protected final EnumMap<GeneType, List<VDJCGene>> genesToAlign;
    protected final List<VDJCGene> usedGenes;
    protected VDJCAlignerEventListener listener = null;

    protected VDJCAligner(VDJCAlignerParameters parameters) {
        this.parameters = parameters.clone();
        this.genesToAlign = new EnumMap<>(GeneType.class);
        this.usedGenes = new ArrayList<>();
        for (GeneType geneType : GeneType.values())
            genesToAlign.put(geneType, new ArrayList<VDJCGene>());
    }

    VDJCAligner(boolean initialized, VDJCAlignerParameters parameters, EnumMap<GeneType, List<VDJCGene>> genesToAlign, List<VDJCGene> usedGenes) {
        this.initialized = initialized;
        this.parameters = parameters;
        this.genesToAlign = genesToAlign;
        this.usedGenes = usedGenes;
    }

    public MiXCRMetaInfo getBaseMetaInfo() {
        return new MiXCRMetaInfo(null, null, parameters, null);
    }

    private static <R extends SequenceRead> long hash(R input) {
        long hash = 1;
        for (int i = 0; i < input.numberOfReads(); i++) {
            final SingleRead r = input.getRead(i);
            hash = 31 * hash + r.getData().getSequence().hashCode();
            if (r.getDescription() != null)
                hash = 31 * hash + r.getDescription().hashCode();
            else
                hash = 31 * hash + HashFunctions.JenkinWang64shift(input.getId());
        }
        return hash;
    }

    @Override
    public final VDJCAlignmentResult<R> process(R input) {
        if (parameters.isFixSeed())
            RandomUtil.reseedThreadLocal(hash(input));
        ensureInitialized();
        return process0(input);
    }

    protected abstract VDJCAlignmentResult<R> process0(final R input);

    public void setEventsListener(VDJCAlignerEventListener listener) {
        this.listener = listener;
    }

    protected final void onFailedAlignment(SequenceRead read, VDJCAlignmentFailCause cause) {
        if (listener != null)
            listener.onFailedAlignment(read, cause);
    }

    protected final void onSuccessfulAlignment(SequenceRead read, VDJCAlignments alignment) {
        if (listener != null) {
            listener.onSuccessfulAlignment(read, alignment);
            if (!alignment.isAvailable(ReferencePoint.CDR3Begin) && !alignment.isAvailable(ReferencePoint.CDR3End))
                listener.onNoCDR3PartsAlignment();
            else if (!alignment.isAvailable(GeneFeature.CDR3))
                listener.onPartialAlignment();
        }
    }

    protected final void onSegmentChimeraDetected(GeneType geneType, SequenceRead read, VDJCAlignments alignment) {
        if (listener != null)
            listener.onSegmentChimeraDetected(geneType, read, alignment);
    }

    public boolean isInitialized() {
        return initialized;
    }

    final void ensureInitialized() {
        if (!initialized)
            synchronized (this) {
                if (!initialized) {
                    // Sorting genes
                    for (List<VDJCGene> genes : genesToAlign.values())
                        Collections.sort(genes);

                    init();
                    initialized = true;
                }
            }
    }

    protected abstract void init();

    public VDJCAlignerParameters getParameters() {
        return parameters.clone();
    }

    public List<VDJCGene> getUsedGenes() {
        return Collections.unmodifiableList(usedGenes);
    }

    public int addGene(VDJCGene gene) {
        usedGenes.add(gene);
        List<VDJCGene> genes = genesToAlign.get(gene.getGeneType());
        genes.add(gene);
        return genes.size() - 1;
    }

    public VDJCGene getGene(GeneType type, int index) {
        return genesToAlign.get(type).get(index);
    }

    public List<VDJCGene> getVGenesToAlign() {
        return genesToAlign.get(GeneType.Variable);
    }

    public List<VDJCGene> getDGenesToAlign() {
        return genesToAlign.get(GeneType.Diversity);
    }

    public List<VDJCGene> getJGenesToAlign() {
        return genesToAlign.get(GeneType.Joining);
    }

    public List<VDJCGene> getCGenesToAlign() {
        return genesToAlign.get(GeneType.Constant);
    }

    public static VDJCAligner createAligner(VDJCAlignerParameters alignerParameters,
                                            boolean paired, boolean merge) {
        return paired ?
                merge ? new VDJCAlignerWithMerge(alignerParameters)
                        : new VDJCAlignerPVFirst(alignerParameters)
                : new VDJCAlignerS(alignerParameters);
    }

    // public static Chains getPossibleDLoci(VDJCHit[] vHits, VDJCHit[] jHits) {
    //     Chains chains = new Chains();
    //     for (VDJCHit h : vHits)
    //         chains = chains.merge(h.getGene().getChains());
    //     for (VDJCHit h : jHits)
    //         chains = chains.merge(h.getGene().getChains());
    //     return chains;
    // }

    /*
     *  Common utility methods
     */

    static Chains getVJCommonChains(final HasGene[] vHits, final HasGene[] jHits) {
        return getChains(vHits).intersection(getChains(jHits));
    }

    /**
     * Merge chains of all V/D/J/C hits.
     *
     * Allows null as input.
     */
    public static Chains getChains(final HasGene[] hits) {
        if (hits == null || hits.length == 0)
            return Chains.ALL;
        Chains chains = hits[0].getGene().getChains();
        for (int i = 1; i < hits.length; i++)
            chains = chains.merge(hits[i].getGene().getChains());
        return chains;
    }

    /**
     * Calculates possible chains for D gene alignment, in the presence of following V, J and C genes.
     *
     * Allows nulls as input.
     */
    public static Chains getPossibleDLoci(HasGene[] vHits, HasGene[] jHits, HasGene[] cHits) {
        Chains intersection = getChains(vHits)
                .intersection(getChains(jHits))
                .intersection(getChains(cHits));

        if (!intersection.isEmpty())
            return intersection;

        // If intersection is empty, we are working with chimera
        // lets calculate all possible D loci by merging
        Chains chains = Chains.EMPTY;
        if (vHits != null)
            for (HasGene h : vHits)
                chains = chains.merge(h.getGene().getChains());
        if (jHits != null)
            for (HasGene h : jHits)
                chains = chains.merge(h.getGene().getChains());
        if (cHits != null)
            for (HasGene h : cHits)
                chains = chains.merge(h.getGene().getChains());

        return chains;
    }

    static HasGene[] wrapAlignmentHits(final AlignmentHit<?, VDJCGene>[] hits) {
        if (hits == null)
            return null;
        HasGene[] res = new HasGene[hits.length];
        for (int i = 0; i < hits.length; i++)
            res[i] = wrapAlignmentHit(hits[i]);
        return res;
    }

    static HasGene wrapAlignmentHit(final AlignmentHit<?, VDJCGene> hit) {
        return hit::getRecordPayload;
    }
}
