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
package com.milaboratory.mixcr.basictypes;

import com.milaboratory.mixcr.util.RunMiXCR;
import com.milaboratory.util.TempFileManager;
import gnu.trove.list.array.TLongArrayList;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.util.concurrent.ThreadLocalRandom;

public class RandomAccessVDJCAReaderTest {
    @Test
    public void test1() throws Exception {
        RunMiXCR.RunMiXCRAnalysis params = new RunMiXCR.RunMiXCRAnalysis(
                RunMiXCR.class.getResource("/sequences/test_R1.fastq").getFile(),
                RunMiXCR.class.getResource("/sequences/test_R2.fastq").getFile());

        RunMiXCR.AlignResult align = RunMiXCR.align(params);

        File file = TempFileManager.getTempFile();
        try (VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(file)) {
            writer.header(align.aligner);
            for (VDJCAlignments alignment : align.alignments)
                writer.write(alignment);
        }

        TLongArrayList index = new TLongArrayList();

        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(file)) {
            reader.setIndexer(index);
            int i = 0;
            VDJCAlignments alignments;
            while ((alignments = reader.take()) != null)
                Assert.assertEquals(align.alignments.get(i++), alignments);
        }

        try (RandomAccessVDJCAReader reader = new RandomAccessVDJCAReader(file, index.toArray())) {
            Assert.assertEquals(align.parameters.alignerParameters, reader.getParameters());
            for (int i = 0; i < 1000; i++) {
                int ind = ThreadLocalRandom.current().nextInt(align.alignments.size());
                VDJCAlignments alignment = reader.get(ind);
                Assert.assertEquals(alignment, align.alignments.get(ind));
            }
        }

    }
}