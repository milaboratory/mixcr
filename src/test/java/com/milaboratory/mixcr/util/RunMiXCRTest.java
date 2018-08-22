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
 * commercial purposes should contact the Inventors using the following email
 * addresses: licensing@milaboratory.com
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
package com.milaboratory.mixcr.util;

import cc.redberry.pipe.CUtils;
import com.milaboratory.core.io.sequence.PairedRead;
import com.milaboratory.core.io.sequence.fastq.PairedFastqReader;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.cli.ActionAlign;
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
            Chains vjLoci = VDJCAligner.getPossibleDLoci(clone.getHits(GeneType.Variable), clone.getHits(GeneType.Joining));
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

        File tempFile = TempFileManager.getTempFile();
        CloneSetIO.write(assemble.cloneSet, tempFile);
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

        //params.library = "human_TR";
        //params.species = "hs";

        RunMiXCR.AlignResult align = RunMiXCR.align(params);

        List<PairedRead> reads = new ArrayList<>();
        try(PairedFastqReader fReader = new PairedFastqReader(
                RunMiXCR.class.getResource("/sequences/test_R1.fastq").getFile(),
                RunMiXCR.class.getResource("/sequences/test_R2.fastq").getFile())) {
            for (PairedRead s : CUtils.it(fReader))
                reads.add(s);
        }

        File tempFile = TempFileManager.getTempFile();
        try(VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(tempFile)) {
            writer.header(align.aligner);
            for (VDJCAlignments alignment : align.alignments)
                writer.write(alignment);
        }

        try(VDJCAlignmentsReader reader = new VDJCAlignmentsReader(tempFile)) {
            int tr = 0;
            for (VDJCAlignments alignment : CUtils.it(reader)) {
                PairedRead actual = reads.get((int) alignment.getReadId());
                ++tr;

                Assert.assertArrayEquals(ActionAlign.extractSequences(actual), alignment.getOriginalSequences());
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
        try(PairedFastqReader fReader = new PairedFastqReader(
                "/Users/poslavsky/Projects/milab/temp/R1_part.fastq.gz",
                "/Users/poslavsky/Projects/milab/temp/R2_part.fastq.gz", true)) {
            for (PairedRead s : CUtils.it(fReader))
                reads.add(s);
        }

        File tempFile = TempFileManager.getTempFile();
        try(VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(tempFile)) {
            writer.header(align.aligner);
            for (VDJCAlignments alignment : align.alignments)
                writer.write(alignment);
        }

        try(VDJCAlignmentsReader reader = new VDJCAlignmentsReader(tempFile)) {
            int tr = 0;
            for (VDJCAlignments alignment : CUtils.it(reader)) {
                PairedRead actual = reads.get((int) alignment.getReadId());
                ++tr;

                Assert.assertArrayEquals(ActionAlign.extractSequences(actual), alignment.getOriginalSequences());
            }

            System.out.println(tr);
        }
    }
}