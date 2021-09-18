/*
 * Copyright (c) 2014-2021, MiLaboratories Inc. All Rights Reserved
 *
 * BEFORE DOWNLOADING AND/OR USING THE SOFTWARE, WE STRONGLY ADVISE
 * AND ASK YOU TO READ CAREFULLY LICENSE AGREEMENT AT:
 *
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 */
package com.milaboratory.mixcr.basictypes;

import cc.redberry.pipe.CUtils;
import com.milaboratory.core.io.sequence.SingleRead;
import com.milaboratory.core.io.sequence.fastq.SingleFastqReader;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerS;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignmentResult;
import com.milaboratory.mixcr.vdjaligners.VDJCParametersPresets;
import io.repseq.core.Chains;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCLibraryRegistry;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class IOTest {
    @Test
    public void testSerialization1() throws Exception {
        VDJCAlignerParameters parameters = VDJCParametersPresets.getByName("default");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        List<VDJCAlignments> alignemntsList = new ArrayList<>();

        int header;

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


            try (VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(bos, 4, 21)) {
                writer.header(aligner, null);

                header = bos.size();

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

        System.out.println("Bytes per alignment: " + (bos.size() - header) / alignemntsList.size());

        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(new ByteArrayInputStream(bos.toByteArray()), true)) {
            int i = 0;
            for (VDJCAlignments alignments : CUtils.it(reader)) {
                Assert.assertEquals(alignments.getAlignmentsIndex(), i);
                assertEquals(alignemntsList.get(i++), alignments);
            }
            Assert.assertEquals(numberOfAlignments, i);
            Assert.assertEquals(numberOfReads, reader.getNumberOfReads());
        }
    }
}
