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
import java.util.Collections;
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
                writer.header(aligner.getBaseMetaInfo(), aligner.getUsedGenes());

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
                writer.writeFooter(Collections.emptyList(), null);
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