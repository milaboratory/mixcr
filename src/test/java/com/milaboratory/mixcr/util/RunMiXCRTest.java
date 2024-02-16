/*
 * Copyright (c) 2014-2024, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
package com.milaboratory.mixcr.util;

import cc.redberry.pipe.CUtils;
import com.milaboratory.core.io.sequence.PairedRead;
import com.milaboratory.core.io.sequence.fastq.PairedFastqReader;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.vdjaligners.VDJCAligner;
import com.milaboratory.util.TempFileManager;
import io.repseq.core.Chains;
import io.repseq.core.GeneType;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static com.milaboratory.mixcr.tests.MiXCRTestUtils.dummyHeader;
import static com.milaboratory.mixcr.tests.MiXCRTestUtils.emptyFooter;

/**
 * Created by poslavsky on 01/09/15.
 */
public class RunMiXCRTest {
    @Test
    public void test1() throws Exception {
        RunMiXCR.RunMiXCRAnalysis params = new RunMiXCR.RunMiXCRAnalysis(
                RunMiXCR.class.getResource("/sequences/test_R1.fastq").getFile(),
                RunMiXCR.class.getResource("/sequences/test_R2.fastq").getFile());

        RunMiXCR.AlignResult align = RunMiXCR.align(params);
        RunMiXCR.AssembleResult assemble = RunMiXCR.assemble(align);

        for (Clone clone : assemble.cloneSet.getClones()) {
            Chains vjLoci = VDJCAligner.Companion.getPossibleDLoci(clone.getHits(GeneType.Variable), clone.getHits(GeneType.Joining),
                    null);
            for (VDJCHit dHit : clone.getHits(GeneType.Diversity))
                Assert.assertTrue(vjLoci.intersects(dHit.getGene().getChains()));
        }
    }

    @Test
    public void testIO() throws Exception {
        RunMiXCR.RunMiXCRAnalysis params = new RunMiXCR.RunMiXCRAnalysis(
                RunMiXCR.class.getResource("/sequences/test_R1.fastq").getFile(),
                RunMiXCR.class.getResource("/sequences/test_R2.fastq").getFile());

        RunMiXCR.AlignResult align = RunMiXCR.align(params);
        RunMiXCR.AssembleResult assemble = RunMiXCR.assemble(align);

        File tempFile = TempFileManager.newTempFile();
        try (ClnsWriter writer = new ClnsWriter(tempFile)) {
            writer.writeCloneSet(assemble.cloneSet);
            writer.setFooter(emptyFooter());
        }
        CloneSet read = CloneSetIO.read(tempFile);

        System.out.println("Clns file size: " + tempFile.length());
        // Before GFRef : Clns file size: 37 372
        // S1 : Clns file size: 41 134
        // After ref: Clns file size: 36 073
        // After checksum to byte[] : Clns file size: 28242

        for (int i = 0; i < read.size(); i++)
            Assert.assertEquals(assemble.cloneSet.get(i), read.get(i));
    }

    //@Test
    //public void testt() throws Exception {
    //    try {
    //        Path cachePath = Paths.get(System.getProperty("user.home"), ".repseqio", "cache");
    //        SequenceResolvers.initDefaultResolver(cachePath);
    //        VDJCLibraryRegistry.getDefault().registerLibraries("/Volumes/Data/Projects/repseqio/reference/human/TRB.json", "mi");
    //        System.out.println(VDJCLibraryRegistry.getDefault().getLibrary("mi", "hs").get("TRBV12-3*00").getFeature(GeneFeature.parse("VRegion(-100,+10000000)")));
    //    } catch (SequenceProviderIndexOutOfBoundsException e) {
    //        System.out.println(e.getAvailableRange());
    //    }
    //}

    @Test
    public void test2() throws Exception {
        //Path cachePath = Paths.get(System.getProperty("user.home"), ".repseqio", "cache");
        //SequenceResolvers.initDefaultResolver(cachePath);
        //VDJCLibraryRegistry.getDefault().addPathResolver("/Volumes/Data/Projects/repseqio/reference/human/");

        RunMiXCR.RunMiXCRAnalysis params = new RunMiXCR.RunMiXCRAnalysis(
                RunMiXCR.class.getResource("/sequences/test_R1.fastq").getFile(),
                RunMiXCR.class.getResource("/sequences/test_R2.fastq").getFile());

        params.alignerParameters.setSaveOriginalReads(true);
        //params.library = "human_TR";
        //params.species = "hs";

        RunMiXCR.AlignResult align = RunMiXCR.align(params);

        List<PairedRead> reads = new ArrayList<>();
        try (PairedFastqReader fReader = new PairedFastqReader(
                RunMiXCR.class.getResource("/sequences/test_R1.fastq").getFile(),
                RunMiXCR.class.getResource("/sequences/test_R2.fastq").getFile())) {
            for (PairedRead s : CUtils.it(fReader))
                reads.add(s);
        }

        File tempFile = TempFileManager.newTempFile();
        try (VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(tempFile)) {
            writer.writeHeader(dummyHeader(), align.aligner.getUsedGenes());
            for (VDJCAlignments alignment : align.alignments)
                writer.write(alignment);
            writer.setFooter(emptyFooter());
        }

        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(tempFile)) {
            int tr = 0;
            for (VDJCAlignments alignment : CUtils.it(reader)) {
                PairedRead actual = reads.get((int) alignment.getMinReadId());
                ++tr;

                Assert.assertEquals(actual, alignment.getOriginalReads().get(0));
            }

            System.out.println(tr);
        }
    }

    @Ignore
    @Test
    public void test3() throws Exception {
        RunMiXCR.RunMiXCRAnalysis params = new RunMiXCR.RunMiXCRAnalysis(
                "/Users/poslavsky/Projects/milab/temp/R1_part.fastq.gz",
                "/Users/poslavsky/Projects/milab/temp/R2_part.fastq.gz");

        RunMiXCR.AlignResult align = RunMiXCR.align(params);

        List<PairedRead> reads = new ArrayList<>();
        try (PairedFastqReader fReader = new PairedFastqReader(
                "/Users/poslavsky/Projects/milab/temp/R1_part.fastq.gz",
                "/Users/poslavsky/Projects/milab/temp/R2_part.fastq.gz", true)) {
            for (PairedRead s : CUtils.it(fReader))
                reads.add(s);
        }

        File tempFile = TempFileManager.newTempFile();
        try (VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(tempFile)) {
            writer.writeHeader(dummyHeader(), align.aligner.getUsedGenes());
            for (VDJCAlignments alignment : align.alignments)
                writer.write(alignment);
            writer.setFooter(emptyFooter());
        }

        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(tempFile)) {
            int tr = 0;
            for (VDJCAlignments alignment : CUtils.it(reader)) {
                PairedRead actual = reads.get((int) alignment.getMinReadId());
                ++tr;

                Assert.assertEquals(actual, alignment.getOriginalReads().get(0));
            }

            System.out.println(tr);
        }
    }

    @Test
    public void test4() throws Exception {
        RunMiXCR.RunMiXCRAnalysis params = new RunMiXCR.RunMiXCRAnalysis(
                RunMiXCR.class.getResource("/sequences/sample_IGH_R1.fastq").getFile(),
                RunMiXCR.class.getResource("/sequences/sample_IGH_R2.fastq").getFile());

        RunMiXCR.AlignResult align = RunMiXCR.align(params);
        RunMiXCR.AssembleResult assemble0 = RunMiXCR.assemble(align, false);
        RunMiXCR.FullSeqAssembleResult assemble = RunMiXCR.assembleContigs(assemble0);

        // try (VDJCAlignmentsReader reader = align.resultReader()) {
        //     for (MiXCRCommandReport r : reader.getFooter().getReports()) {
        //         System.out.println(r);
        //     }
        // }
        //
        // try (ClnAReader reader = assemble0.resultReader()) {
        //     for (MiXCRCommandReport r : reader.getFooter().getReports()) {
        //         System.out.println(r);
        //     }
        // }

        for (Clone clone : assemble.cloneSet.getClones()) {
            Chains vjLoci = VDJCAligner.Companion.getPossibleDLoci(clone.getHits(GeneType.Variable), clone.getHits(GeneType.Joining),
                    null);
            for (VDJCHit dHit : clone.getHits(GeneType.Diversity))
                Assert.assertTrue(vjLoci.intersects(dHit.getGene().getChains()));

            //ActionExportClonesPretty.outputCompact(System.out, clone);
        }
    }
}
