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
import com.milaboratory.mixcr.basictypes.tag.TagInfo;
import com.milaboratory.mixcr.basictypes.tag.TagTuple;
import com.milaboratory.primitivio.annotations.Serializable;
import gnu.trove.iterator.TObjectDoubleIterator;
import io.repseq.core.GeneType;

import java.util.EnumMap;
import java.util.Objects;

@Serializable(by = IO.CloneSerializer.class)
public final class Clone extends VDJCObject {
    final double count;
    final int id;
    CloneSetInfo parent = null;
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

    /**
     * Returns new instance with parent clone set set to null
     */
    public Clone resetParentCloneSet() {
        return new Clone(targets, hits, tagCount, count, id, group);
    }

    public void setParentCloneSet(CloneSetInfo set) {
        if (this.parent != null)
            throw new IllegalStateException("Parent is already set.");
        this.parent = set;
    }

    public CloneSetInfo getParentCloneSet() {
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
        if (totalFractions == null)
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

    public double getTagDiversityFraction(int level) {
        return 1.0 * tagCount.getTagDiversity(level) / parent.getTagDiversity(level);
    }

    public double getCount() {
        return count;
    }

    public int getId() {
        return id;
    }

    public Clone[] splitByTag(TagInfo tag) {
        if (tag == null) {
            return new Clone[]{this};
        } else {
            double sum = tagCount.sum();
            TagCount[] splitTagCounts = tagCount.splitBy(tag.getIndex() + 1);
            Clone[] result = new Clone[splitTagCounts.length];
            for (int i = 0; i < splitTagCounts.length; i++) {
                TagCount tc = splitTagCounts[i];
                Clone split = new Clone(targets, hits, tc, 1.0 * count * tc.sum() / sum, id, group);
                split.setParentCloneSet(getParentCloneSet());
                result[i] = split;
            }
            return result;
        }
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
                id == clone.id && Objects.equals(group, clone.group);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), count, id, group);
    }
}
