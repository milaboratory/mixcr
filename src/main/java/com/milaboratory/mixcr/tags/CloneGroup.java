/*
 * Copyright (c) 2014-2020, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
