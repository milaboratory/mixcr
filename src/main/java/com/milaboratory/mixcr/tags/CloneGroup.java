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
package com.milaboratory.mixcr.tags;

import gnu.trove.set.hash.TIntHashSet;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class CloneGroup {
    public final int groupId;
    public final double reads;
    public final long umis;
    public final Set<String> groupTags;
    public final TIntHashSet groupClones;

    public CloneGroup(int groupId,
                      double reads, long umis,
                      Set<String> groupTags, TIntHashSet groupClones) {
        this.groupId = groupId;
        this.reads = reads;
        this.umis = umis;
        this.groupTags = groupTags;
        this.groupClones = groupClones;
    }

    public CloneGroup mergeFrom(CloneGroup other) {
        assert groupClones.containsAll(other.groupClones);

        HashSet<String> newTags = new HashSet<>(groupTags);
        newTags.addAll(other.groupTags);
        return new CloneGroup(
                groupId,
                reads + other.reads,
                umis + other.umis,
                newTags,
                groupClones);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CloneGroup that = (CloneGroup) o;
        return groupId == that.groupId &&
                Double.compare(that.reads, reads) == 0 &&
                umis == that.umis &&
                Objects.equals(groupTags, that.groupTags) &&
                Objects.equals(groupClones, that.groupClones);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, reads, umis, groupTags, groupClones);
    }
}
