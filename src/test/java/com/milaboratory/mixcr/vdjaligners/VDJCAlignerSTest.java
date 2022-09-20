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
package com.milaboratory.mixcr.vdjaligners;

import cc.redberry.pipe.CUtils;
import com.milaboratory.core.io.sequence.SingleRead;
import com.milaboratory.core.io.sequence.SingleReadImpl;
import com.milaboratory.core.io.sequence.fastq.SingleFastqReader;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceQuality;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter;
import com.milaboratory.util.RandomUtil;
import com.milaboratory.util.TempFileManager;
import io.repseq.core.Chains;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCLibraryRegistry;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.milaboratory.mixcr.tests.MiXCRTestUtils.emptyFooter;

public class VDJCAlignerSTest {
    @Test
    public void testSerialization1() throws Exception {
        VDJCAlignerParameters parameters =
                VDJCParametersPresets.getByName("default");
        //LociLibrary ll = LociLibraryManager.getDefault().getLibrary("mi");
        File tmpFile = TempFileManager.getTempFile();

        List<VDJCAlignments> alignemntsList = new ArrayList<>();
        long header;
        try (SingleFastqReader reader =
                     new SingleFastqReader(
                             VDJCAlignerSTest.class.getClassLoader()
                                     .getResourceAsStream("sequences/sample_IGH_R1.fastq"), true)) {
            VDJCAlignerS aligner = new VDJCAlignerS(parameters);
            for (VDJCGene gene : VDJCLibraryRegistry.getDefault().getLibrary("default", "hs").getGenes(Chains.IGH))
                if (parameters.containsRequiredFeature(gene))
                    aligner.addGene(gene);
            try (VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(tmpFile)) {
                writer.writeHeader(aligner.getBaseMetaInfo(), aligner.getUsedGenes());
                header = writer.getPosition();
                for (SingleRead read : CUtils.it(reader)) {
                    VDJCAlignmentResult<SingleRead> result = aligner.process(read);
                    if (result.alignment != null) {
                        writer.write(result.alignment);
                        alignemntsList.add(result.alignment);
                    }
                }
                writer.setFooter(emptyFooter());
            }
        }
        Assert.assertTrue(alignemntsList.size() > 10);
        System.out.println("Bytes per alignment: " + (Files.size(tmpFile.toPath()) - header) / alignemntsList.size());
        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(tmpFile)) {
            int i = 0;
            for (VDJCAlignments alignments : CUtils.it(reader))
                Assert.assertEquals(alignemntsList.get(i++), alignments);
        }

        tmpFile.delete();
    }

    @Test
    @Ignore
    public void test2() throws Exception {
        //        @
        //                GCTGTGTATTACTGTGCAAGAGGGCCCCAAGAAAATAGTGGTTATTACTACGGGTTTGACTACTGGGGCCAGGGAACCCTGGTCACCGTCTCCTCAGCCTCCACCAAGGGCCCATCGGTCTTCCCCCTGGCGCC
        //        +
        //                CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC

        VDJCAlignerParameters parameters =
                VDJCParametersPresets.getByName("default");
        VDJCAlignerS aligner = new VDJCAlignerS(parameters);
        for (VDJCGene gene : VDJCLibraryRegistry.getDefault().getLibrary("default", "hs").getGenes(Chains.IGH))
            if (parameters.containsRequiredFeature(gene))
                aligner.addGene(gene);
        SingleReadImpl read = new SingleReadImpl(0, new NSequenceWithQuality(new NucleotideSequence("GCTGTGTATTACTGTGCAAGAGGGCCCCAAGAAAATAGTGGTTATTACTACGGGTTTGACTACTGGGGCCAGGGAACCCTGGTCACCGTCTCCTCAGCCTCCACCAAGGGCCCATCGGTCTTCCCCCTGGCGCC"), SequenceQuality.GOOD_QUALITY_VALUE), "");
        RandomUtil.getThreadLocalRandom().setSeed(29);
        VDJCAlignmentResult<SingleRead> result = aligner.process0(read);
    }
}