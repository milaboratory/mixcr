/*
 * Copyright (c) 2014-2018, Bolotin Dmitry, Chudakov Dmitry, Shugay Mikhail
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
