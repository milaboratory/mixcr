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
package com.milaboratory.mixcr.assembler;

import cc.redberry.pipe.OutputPort;
import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;

public final class AlignmentsMappingMerger implements OutputPortCloseable<VDJCAlignments> {
    final OutputPort<VDJCAlignments> alignments;
    final OutputPort<ReadToCloneMapping> readToCloneMappings;
    ReadToCloneMapping lastMapping;

    public AlignmentsMappingMerger(OutputPort<VDJCAlignments> alignments, OutputPort<ReadToCloneMapping> readToCloneMappings) {
        this.alignments = alignments;
        this.readToCloneMappings = readToCloneMappings;
    }

    @Override
    public VDJCAlignments take() {
        VDJCAlignments al = alignments.take();
        if (al == null) {
            assert readToCloneMappings.take() == null;
            return null;
        }

        // here cloneIndex is set by a pre-clone block
        // -1 == clone not included into any pre-clone

        if (al.getCloneIndex() == -1)
            return al;

        if(lastMapping == null || lastMapping.getPreCloneIdx() != al.getCloneIndex())
            lastMapping = readToCloneMappings.take();
        assert lastMapping.getPreCloneIdx() == al.getCloneIndex();

        return al.setMapping(lastMapping);
    }

    @Override
    public void close() {
        try {
            if (alignments instanceof AutoCloseable)
                ((AutoCloseable) alignments).close();
            if (readToCloneMappings instanceof AutoCloseable)
                ((AutoCloseable) readToCloneMappings).close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
