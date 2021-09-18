/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.basictypes;

import cc.redberry.primitives.Filter;
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCGeneId;

import java.util.*;

/**
 * Created by poslavsky on 10/07/14.
 */
public final class CloneSet implements Iterable<Clone> {
    String versionInfo;
    final CloneAssemblerParameters assemblerParameters;
    final VDJCAlignerParameters alignmentParameters;
    final EnumMap<GeneType, GeneFeature> alignedFeatures;
    final List<VDJCGene> usedGenes;
    final List<Clone> clones;
    final long totalCount;

    public CloneSet(List<Clone> clones, Collection<VDJCGene> usedGenes, EnumMap<GeneType, GeneFeature> alignedFeatures,
                    VDJCAlignerParameters alignmentParameters, CloneAssemblerParameters assemblerParameters) {
        this.clones = Collections.unmodifiableList(new ArrayList<>(clones));
        long totalCount = 0;
        for (Clone clone : clones) {
            totalCount += clone.count;
            clone.setParentCloneSet(this);
        }
        this.alignedFeatures = alignedFeatures.clone();
        this.alignmentParameters = alignmentParameters;
        this.assemblerParameters = assemblerParameters;
        this.usedGenes = Collections.unmodifiableList(new ArrayList<>(usedGenes));
        this.totalCount = totalCount;
    }

    public CloneSet(List<Clone> clones) {
        this.clones = Collections.unmodifiableList(new ArrayList<>(clones));
        long totalCount = 0;
        HashMap<VDJCGeneId, VDJCGene> genes = new HashMap<>();
        EnumMap<GeneType, GeneFeature> alignedFeatures = new EnumMap<>(GeneType.class);
        for (Clone clone : clones) {
            totalCount += clone.count;
            clone.setParentCloneSet(this);
            for (GeneType geneType : GeneType.values())
                for (VDJCHit hit : clone.getHits(geneType)) {
                    VDJCGene gene = hit.getGene();
                    genes.put(gene.getId(), gene);
                    GeneFeature alignedFeature = hit.getAlignedFeature();
                    GeneFeature f = alignedFeatures.put(geneType, alignedFeature);
                    if (f != null && !f.equals(alignedFeature))
                        throw new IllegalArgumentException("Different aligned feature for clones.");
                }
        }
        this.alignedFeatures = alignedFeatures;
        this.assemblerParameters = null;
        this.alignmentParameters = null;
        this.usedGenes = Collections.unmodifiableList(new ArrayList<>(genes.values()));
        this.totalCount = totalCount;
    }

    public List<Clone> getClones() {
        return clones;
    }

    public Clone get(int i) {
        return clones.get(i);
    }

    public int size() {
        return clones.size();
    }

    public GeneFeature[] getAssemblingFeatures() {
        return assemblerParameters.getAssemblingFeatures();
    }

    public CloneAssemblerParameters getAssemblerParameters() {
        return assemblerParameters;
    }

    public VDJCAlignerParameters getAlignmentParameters() {
        return alignmentParameters;
    }

    public List<VDJCGene> getUsedGenes() {
        return usedGenes;
    }

    public EnumMap<GeneType, GeneFeature> getAlignedFeatures() {
        return new EnumMap<>(alignedFeatures);
    }

    public GeneFeature getAlignedGeneFeature(GeneType geneType) {
        return alignedFeatures.get(geneType);
    }

    public long getTotalCount() {
        return totalCount;
    }

    public String getVersionInfo() {
        return versionInfo;
    }

    @Override
    public Iterator<Clone> iterator() {
        return clones.iterator();
    }

    /**
     * WARNING: current object will be destroyed
     */
    public static CloneSet transform(CloneSet in, Filter<Clone> filter) {
        List<Clone> newClones = new ArrayList<>(in.size());
        for (int i = 0; i < in.size(); ++i) {
            Clone c = in.get(i);
            if (filter.accept(c)) {
                c.parent = null;
                newClones.add(c);
            }
        }
        return new CloneSet(newClones, in.usedGenes, in.alignedFeatures, in.alignmentParameters, in.assemblerParameters);
    }
}
