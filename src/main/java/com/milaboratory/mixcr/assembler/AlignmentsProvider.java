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
import io.repseq.core.VDJCLibraryRegistry;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public interface AlignmentsProvider {
    OutputPortCloseable<VDJCAlignments> create();

    /**
     * Should return total number of reads (not alignments) after whole analysis if such information available.
     *
     * @return total number of reads
     */
    long getTotalNumberOfReads();

    final class Util {
        static AlignmentsProvider createProvider(final byte[] rawData, final VDJCLibraryRegistry geneResolver) {
            return new VDJCAlignmentsReaderWrapper(() ->
                    new VDJCAlignmentsReader(new ByteArrayInputStream(rawData), geneResolver, rawData.length, true)
            );
        }

        public static AlignmentsProvider createProvider(final String file, final VDJCLibraryRegistry geneResolver) {
            return new VDJCAlignmentsReaderWrapper(() -> {
                try {
                    return new VDJCAlignmentsReader(file, geneResolver, true);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }
}
