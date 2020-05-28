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

import cc.redberry.pipe.CUtils;
import com.milaboratory.core.io.sequence.SingleRead;
import com.milaboratory.core.io.sequence.fastq.SingleFastqReader;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerS;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentResult;
import com.milaboratory.mixcr.vdjaligners.VDJCParametersPresets;
import com.milaboratory.util.TempFileManager;
import io.repseq.core.Chains;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCLibraryRegistry;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IOTest {
    @Test
    public void testSerialization1() throws Exception {
        VDJCAlignerParameters parameters = VDJCParametersPresets.getByName("default");

        File tmpFile = TempFileManager.getTempFile();

        List<VDJCAlignments> alignemntsList = new ArrayList<>();

        long header;

        long numberOfReads;
        long numberOfAlignments = 0;
        try (SingleFastqReader reader =
                     new SingleFastqReader(
                             IOTest.class.getClassLoader()
                                     .getResourceAsStream("sequences/sample_IGH_R1.fastq"), true)) {

            VDJCAlignerS aligner = new VDJCAlignerS(parameters);

            for (VDJCGene gene : VDJCLibraryRegistry.getDefault().getLibrary("default", "hs").getGenes(Chains.IGH)) {
                if (parameters.containsRequiredFeature(gene))
                    aligner.addGene(gene);
            }


            try (VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(tmpFile)) {
                writer.header(aligner, null);

                header = writer.getPosition();

                for (SingleRead read : CUtils.it(reader)) {
                    VDJCAlignmentResult<SingleRead> result = aligner.process(read);
                    if (result.alignment != null) {
                        numberOfAlignments++;
                        writer.write(result.alignment);
                        alignemntsList.add(result.alignment);
                    }
                }

                writer.setNumberOfProcessedReads(numberOfReads = reader.getNumberOfReads());
            }
        }

        assertTrue(alignemntsList.size() > 10);
        assertTrue(numberOfReads > 10);

        System.out.println("Bytes per alignment: " + (Files.size(tmpFile.toPath()) - header) / alignemntsList.size());

        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(tmpFile)) {
            int i = 0;
            for (VDJCAlignments alignments : CUtils.it(reader)) {
                Assert.assertEquals(alignments.getAlignmentsIndex(), i);
                assertEquals(alignemntsList.get(i++), alignments);
            }
            Assert.assertEquals(numberOfAlignments, i);
            Assert.assertEquals(numberOfReads, reader.getNumberOfReads());
        }

        tmpFile.delete();
    }
}