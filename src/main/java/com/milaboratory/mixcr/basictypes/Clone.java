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
package com.milaboratory.mixcr.basictypes;

import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.mixcr.basictypes.tag.TagCount;
import com.milaboratory.mixcr.basictypes.tag.TagCountAggregator;
import com.milaboratory.mixcr.basictypes.tag.TagTuple;
import com.milaboratory.primitivio.annotations.Serializable;
import gnu.trove.iterator.TObjectDoubleIterator;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;

import java.util.*;

@Serializable(by = IO.CloneSerializer.class)
public final class Clone extends VDJCObject {
    final double count;
    final int id;
    CloneSet parent = null;
    final Integer group;

    public Clone(NSequenceWithQuality[] targets, EnumMap<GeneType, VDJCHit[]> hits, TagCount tagCount, double count, int id, Integer group) {
        super(hits, tagCount, targets);
        this.count = count;
        this.id = id;
        this.group = group;
    }

    public Clone setCount(double count) {
        return new Clone(targets, hits, tagCount, count, id, group);
    }

    public Clone setGroup(Integer group) {
        return new Clone(targets, hits, tagCount, count, id, group);
    }

    public Integer getGroup() {
        return group;
    }

    public Clone setId(int id) {
        Clone r = new Clone(targets, hits, tagCount, count, id, group);
        r.setParentCloneSet(parent);
        return r;
    }

    /** Returns new instance with parent clone set set to null */
    public Clone resetParentCloneSet() {
        return new Clone(targets, hits, tagCount, count, id, group);
    }

    public void setParentCloneSet(CloneSet set) {
        if (this.parent != null)
            throw new IllegalStateException("Parent is already set.");
        this.parent = set;
    }

    public CloneSet getParentCloneSet() {
        return parent;
    }

    public Clone setTagCount(TagCount tc, boolean resetCount) {
        Objects.requireNonNull(tc);
        Clone c = new Clone(targets, hits, tc, resetCount ? tc.sum() : count, id, group);
        c.setParentCloneSet(getParentCloneSet());
        return c;
    }

    private double fractionOverride = Double.NaN;

    public void overrideFraction(double v) {
        fractionOverride = v;
    }

    public double getFraction() {
        if (!Double.isNaN(fractionOverride))
            return fractionOverride;

        if (parent == null)
            return Double.NaN;
        return getFraction(parent.getTotalCount());
    }

    public TagCount getTagFractions() {
        if (parent == null)
            return null;
        TagCount totalFractions = parent.getTotalTagCounts();
        if(totalFractions == null)
            throw new IllegalStateException("Wrong parent (parent cloneset with no clones).");

        TagCountAggregator result = new TagCountAggregator();
        TObjectDoubleIterator<TagTuple> it = tagCount.iterator();
        while (it.hasNext()) {
            it.advance();
            TagTuple tt = it.key();
            result.add(tt, it.value() / totalFractions.get(tt));
        }

        return result.createAndDestroy();
    }

    public double getFraction(double totalCount) {
        return count / totalCount;
    }

    public double getCount() {
        return count;
    }

    public int getId() {
        return id;
    }

    // TODO weak/soft link ?
    private HashMap<String, String> geneLabelsCache = null;

    public synchronized String getGeneLabel(String labelName) {
        if (geneLabelsCache == null)
            geneLabelsCache = new HashMap<>();
        // Null values can be present in the cache, so containsKey is the only way to determine cache hit
        if (geneLabelsCache.containsKey(labelName))
            return geneLabelsCache.get(labelName);
        List<String> labelValues = new ArrayList<>();
        for (VDJCGene gene : getBestHitGenes()) {
            String labelValue = gene.getLabel(labelName);
            if (labelValue != null)
                // Using list as a lean set
                if (!labelValues.contains(labelValue))
                    labelValues.add(labelValue);
        }
        // Canonicalization
        Collections.sort(labelValues);
        String labelValue;
        if (labelValues.size() == 0)
            labelValue = null;
        else if (labelValues.size() == 1)
            labelValue = labelValues.get(0);
        else
            labelValue = String.join(",", labelValues);
        // Saving to cache
        geneLabelsCache.put(labelName, labelValue);
        return labelValue;
    }

    @Override
    public String toString() {
        return "id: " + id + " " + "count: " + count;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Clone)) return false;
        if (!super.equals(o)) return false;
        Clone clone = (Clone) o;
        return Double.compare(clone.count, count) == 0 &&
                id == clone.id && group == clone.group;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), count, id, group);
    }
}
