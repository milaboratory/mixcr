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
package com.milaboratory.mixcr.vdjaligners;

import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.tag.TagTuple;
import io.repseq.core.GeneType;

import java.util.Set;

public final class VDJCAlignmentResult<R extends SequenceRead> {
    public final R read;
    public final VDJCAlignments alignment;
    public final TagTuple tagTuple;

    public VDJCAlignmentResult(R read, VDJCAlignments alignment) {
        this.read = read;
        this.alignment = alignment;
        this.tagTuple = null;
    }

    public VDJCAlignmentResult(R read) {
        this.read = read;
        this.alignment = null;
        this.tagTuple = null;
    }

    public VDJCAlignmentResult(R read, TagTuple tagTuple) {
        this.read = read;
        this.alignment = null;
        this.tagTuple = tagTuple;
    }

    public VDJCAlignmentResult(R read, VDJCAlignments alignment, TagTuple tagTuple) {
        this.read = read;
        this.alignment = alignment;
        this.tagTuple = tagTuple;
    }

    public VDJCAlignmentResult<R> withTagTuple(TagTuple tagTuple) {
        return new VDJCAlignmentResult<>(read, alignment, tagTuple);
    }

    public VDJCAlignmentResult<R> shiftIndelsAtHomopolymers(Set<GeneType> gts) {
        if (alignment == null)
            return this;
        return new VDJCAlignmentResult<>(read, alignment.shiftIndelsAtHomopolymers(gts), tagTuple);
    }
}
