/*
 * Copyright (c) 2014-2019, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
 * commercial purposes should contact MiLaboratory LLC, which owns exclusive
 * rights for distribution of this program for commercial purposes, using the
 * following email address: licensing@milaboratory.com.
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

import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.primitivio.annotations.Serializable;
import gnu.trove.iterator.TObjectDoubleIterator;
import io.repseq.core.GeneType;

import java.util.EnumMap;
import java.util.Objects;

@Serializable(by = IO.CloneSerializer.class)
public final class Clone extends VDJCObject {
    final double count;
    final int id;
    CloneSet parent = null;
    final Integer group;

    public Clone(NSequenceWithQuality[] targets, EnumMap<GeneType, VDJCHit[]> hits, TagCounter tagCounter, double count, int id, Integer group) {
        super(hits, tagCounter, targets);
        this.count = count;
        this.id = id;
        this.group = group;
    }

    public Clone setCount(double count) {
        return new Clone(targets, hits, tagCounter, count, id, group);
    }

    public Clone setGroup(Integer group) {
        return new Clone(targets, hits, tagCounter, count, id, group);
    }

    public Integer getGroup() {
        return group;
    }

    public Clone setId(int id) {
        Clone r = new Clone(targets, hits, tagCounter, count, id, group);
        r.setParentCloneSet(parent);
        return r;
    }

    /** Returns new instance with parent clone set set to null */
    public Clone resetParentCloneSet() {
        return new Clone(targets, hits, tagCounter, count, id, group);
    }

    public void setParentCloneSet(CloneSet set) {
        if (this.parent != null)
            throw new IllegalStateException("Parent is already set.");
        this.parent = set;
    }

    public CloneSet getParentCloneSet() {
        return parent;
    }

    public Clone setTagCounts(TagCounter tc) {
        Clone c = new Clone(targets, hits, tc, count, id, group);
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

    public TagCounter getTagFractions() {
        if (parent == null)
            return null;
        TagCounter totalFractions = parent.getTotalTagCounts();

        TagCounterBuilder result = new TagCounterBuilder();
        TObjectDoubleIterator<TagTuple> it = tagCounter.iterator();
        while (it.hasNext()) {
            it.advance();
            TagTuple tt = it.key();
            result.add(tt, it.value() / totalFractions.get(tt));
        }

        return result.createAndDestroy();
    }

    public double getFraction(double totalCount) {
        return 1.0 * count / totalCount;
    }

    public double getCount() {
        return count;
    }

    public int getId() {
        return id;
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
