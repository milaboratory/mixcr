/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.assembler;

import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.util.CanReportProgress;
import com.milaboratory.util.Factory;

import java.util.concurrent.atomic.AtomicLong;

class VDJCAlignmentsReaderWrapper implements AlignmentsProvider {
    final Factory<VDJCAlignmentsReader> factory;
    final AtomicLong totalNumberOfReads = new AtomicLong(-1);

    VDJCAlignmentsReaderWrapper(Factory<VDJCAlignmentsReader> factory) {
        this.factory = factory;
    }

    @Override
    public OutputPortCloseable<VDJCAlignments> create() {
        return new OP(factory.create());
    }

    @Override
    public long getTotalNumberOfReads() {
        return totalNumberOfReads.get();
    }

    public class OP implements OutputPortCloseable<VDJCAlignments>, CanReportProgress {
        public final VDJCAlignmentsReader reader;

        private OP(VDJCAlignmentsReader reader) {
            this.reader = reader;
        }

        @Override
        public VDJCAlignments take() {
            VDJCAlignments alignments = reader.take();
            if (alignments == null)
                totalNumberOfReads.set(reader.getNumberOfReads());
            return alignments;
        }

        @Override
        public double getProgress() {
            return reader.getProgress();
        }

        @Override
        public boolean isFinished() {
            return reader.isFinished();
        }

        @Override
        public void close() {
            reader.close();
        }
    }
}
