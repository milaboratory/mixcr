package com.milaboratory.mixcr.util;

import cc.redberry.pipe.CUtils;
import com.milaboratory.core.io.sequence.PairedRead;
import com.milaboratory.core.io.sequence.fastq.PairedFastqReader;
import com.milaboratory.mixcr.assembler.ReadToCloneMapping;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.cli.ActionAlign;
import com.milaboratory.mixcr.reference.GeneType;
import com.milaboratory.mixcr.reference.LociLibraryManager;
import com.milaboratory.mixcr.reference.Locus;
import com.milaboratory.mixcr.vdjaligners.VDJCAligner;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
            Set<Locus> vjLoci = VDJCAligner.getPossibleDLoci(clone.getHits(GeneType.Variable), clone.getHits(GeneType.Joining));
            for (VDJCHit dHit : clone.getHits(GeneType.Diversity))
                Assert.assertTrue(vjLoci.contains(dHit.getAllele().getLocus()));
        }
    }

    @Test
    public void test2() throws Exception {
        RunMiXCR.RunMiXCRAnalysis params = new RunMiXCR.RunMiXCRAnalysis(
                RunMiXCR.class.getResource("/sequences/test_R1.fastq").getFile(),
                RunMiXCR.class.getResource("/sequences/test_R2.fastq").getFile());

        RunMiXCR.AlignResult align = RunMiXCR.align(params);

        List<PairedRead> reads = new ArrayList<>();
        try (PairedFastqReader fReader = new PairedFastqReader(
                RunMiXCR.class.getResource("/sequences/test_R1.fastq").getFile(),
                RunMiXCR.class.getResource("/sequences/test_R2.fastq").getFile())) {
            for (PairedRead s : CUtils.it(fReader))
                reads.add(s);
        }

        File tempFile = TempFileManager.getTempFile();
        try (VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(tempFile)) {
            writer.header(align.aligner);
            for (VDJCAlignments alignment : align.alignments)
                writer.write(alignment);
        }

        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(tempFile, LociLibraryManager.getDefault())) {
            int tr = 0;
            for (VDJCAlignments alignment : CUtils.it(reader)) {
                PairedRead actual = reads.get((int) alignment.getReadId());
                ++tr;

                Assert.assertArrayEquals(ActionAlign.extractNSeqs(actual), alignment.getOriginalSequences());
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

        File tempFile = TempFileManager.getTempFile();
        try (VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(tempFile)) {
            writer.header(align.aligner);
            for (VDJCAlignments alignment : align.alignments)
                writer.write(alignment);
        }

        try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(tempFile, LociLibraryManager.getDefault())) {
            int tr = 0;
            for (VDJCAlignments alignment : CUtils.it(reader)) {
                PairedRead actual = reads.get((int) alignment.getReadId());
                ++tr;

                Assert.assertArrayEquals(ActionAlign.extractNSeqs(actual), alignment.getOriginalSequences());
            }

            System.out.println(tr);
        }
    }

    @Test
    public void test4() throws Exception {
        RunMiXCR.RunMiXCRAnalysis params = new RunMiXCR.RunMiXCRAnalysis(
                RunMiXCR.class.getResource("/sequences/test_R1.fastq").getFile(),
                RunMiXCR.class.getResource("/sequences/test_R2.fastq").getFile());
//        RunMiXCR.RunMiXCRAnalysis params = new RunMiXCR.RunMiXCRAnalysis(
//                "/Users/poslavsky/Projects/milab/temp/R1_part.fastq.gz",
//                "/Users/poslavsky/Projects/milab/temp/R2_part.fastq.gz");
        params.cloneAssemblerParameters.setCloneClusteringParameters(null);

        RunMiXCR.AlignResult align = RunMiXCR.align(params);
        RunMiXCR.AssembleResult assemble = RunMiXCR.assemble(align);

        int id = 0;
        for (ReadToCloneMapping rc : CUtils.it(assemble.assembledReadsPort)) {
//            if (rc.getMappingType() != ReadToCloneMapping.MappingType.Dropped)
//                System.out.println(rc.getCloneIndex());
            Assert.assertEquals(id++, rc.getAlignmentsId());
        }
    }
}