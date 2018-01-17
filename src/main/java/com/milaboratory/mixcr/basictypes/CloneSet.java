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
package com.milaboratory.mixcr.basictypes;

import cc.redberry.primitives.Filter;
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
    final GeneFeature[] assemblingFeatures;
    final EnumMap<GeneType, GeneFeature> alignedFeatures;
    final List<VDJCGene> usedGenes;
    final List<Clone> clones;
    final long totalCount;

    public CloneSet(List<Clone> clones, Collection<VDJCGene> usedGenes, EnumMap<GeneType, GeneFeature> alignedFeatures,
                    GeneFeature[] assemblingFeatures) {
        this.clones = Collections.unmodifiableList(new ArrayList<>(clones));
        long totalCount = 0;
        for (Clone clone : clones) {
            totalCount += clone.count;
            clone.setParentCloneSet(this);
        }
        this.alignedFeatures = alignedFeatures.clone();
        this.usedGenes = Collections.unmodifiableList(new ArrayList<>(usedGenes));
        this.totalCount = totalCount;
        this.assemblingFeatures = assemblingFeatures;
    }

    public CloneSet(List<Clone> clones) {
        this.clones = Collections.unmodifiableList(new ArrayList<>(clones));
        long totalCount = 0;
        HashMap<VDJCGeneId, VDJCGene> genes = new HashMap<>();
        EnumMap<GeneType, GeneFeature> alignedFeatures = new EnumMap<>(GeneType.class);
        GeneFeature[] assemblingFeatures = null;
        for (Clone clone : clones) {
            totalCount += clone.count;
            clone.setParentCloneSet(this);
            if (assemblingFeatures == null)
                assemblingFeatures = clone.getAssemblingFeatures();
            else if (!Arrays.equals(assemblingFeatures, clone.getAssemblingFeatures()))
                throw new IllegalArgumentException("Different assembling features.");
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
        this.assemblingFeatures = assemblingFeatures;
        this.alignedFeatures = alignedFeatures;
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
        return assemblingFeatures;
    }

    public List<VDJCGene> getUsedGenes() {
        return usedGenes;
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
        return new CloneSet(newClones, in.usedGenes, in.alignedFeatures, in.assemblingFeatures);
    }
}
