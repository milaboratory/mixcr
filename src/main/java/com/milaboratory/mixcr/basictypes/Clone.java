/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 * 
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 * 
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.basictypes;

import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.primitivio.annotations.Serializable;
import io.repseq.core.GeneType;

import java.util.EnumMap;
import java.util.Objects;

@Serializable(by = IO.CloneSerializer.class)
public final class Clone extends VDJCObject {
    final double count;
    final int id;
    CloneSet parent = null;

    public Clone(NSequenceWithQuality[] targets, EnumMap<GeneType, VDJCHit[]> hits, double count, int id) {
        super(hits, targets);
        this.count = count;
        this.id = id;
    }

    public Clone setId(int id) {
        Clone r = new Clone(targets, hits, count, id);
        r.setParentCloneSet(parent);
        return r;
    }

    /** Returns new instance with parent clone set set to null */
    public Clone resetParentCloneSet() {
        return new Clone(targets, hits, count, id);
    }

    public void setParentCloneSet(CloneSet set) {
        if (this.parent != null)
            throw new IllegalStateException("Parent is already set.");
        this.parent = set;
    }

    public CloneSet getParentCloneSet() {
        return parent;
    }

    public double getFraction() {
        if (parent == null)
            return Double.NaN;
        return getFraction(parent.getTotalCount());
    }

    public double getFraction(long totalCount) {
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
                id == clone.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), count, id);
    }
}
