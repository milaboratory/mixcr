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
import com.milaboratory.core.io.sequence.PairedRead;
import com.milaboratory.core.io.sequence.SingleReadImpl;
import com.milaboratory.core.io.sequence.fastq.PairedFastqReader;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceQuality;
import com.milaboratory.mixcr.basictypes.MultiAlignmentHelper;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsFormatter;
import com.milaboratory.mixcr.basictypes.VDJCHit;
import com.milaboratory.mixcr.util.RunMiXCR;
import io.repseq.core.Chains;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCLibraryRegistry;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class VDJCAlignerPVFirstTest {
    @Test
    public void test1() throws Exception {
        VDJCAlignerParameters parameters =
                VDJCParametersPresets
                        .getByName("default");

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        List<VDJCAlignments> alignemntsList = new ArrayList<>();

        int header;

        int total = 0;
        int leftHit = 0;

        try (PairedFastqReader reader =
                     new PairedFastqReader(
                             VDJCAlignerSTest.class.getClassLoader()
                                     .getResourceAsStream("sequences/sample_IGH_R1.fastq"),
                             VDJCAlignerSTest.class.getClassLoader()
                                     .getResourceAsStream("sequences/sample_IGH_R2.fastq"), true)) {

            VDJCAlignerPVFirst aligner = new VDJCAlignerPVFirst(parameters);

            for (VDJCGene gene : VDJCLibraryRegistry.getDefault().getLibrary("default", "hs").getGenes(Chains.IGH)) {
                if (parameters.containsRequiredFeature(gene))
                    aligner.addGene(gene);
            }

            for (PairedRead read : CUtils.it(reader)) {
                ++total;
                VDJCAlignments result = aligner.process(read.toTuple(), read);
                if (result != null) {
                    alignemntsList.add(result);
                    for (VDJCHit hit : result.getHits(GeneType.Variable))
                        if (hit.getAlignment(0) != null && hit.getAlignment(1) != null)
                            ++leftHit;
                }
            }
        }

        System.out.println(alignemntsList.size());
        System.out.println(total);
        System.out.println(leftHit);
        Assert.assertTrue(alignemntsList.size() > 10);

        int k = 10;

        for (VDJCAlignments alignments : alignemntsList) {
            for (int target = 0; target < alignments.numberOfTargets(); target++) {
                MultiAlignmentHelper helperBig = VDJCAlignmentsFormatter.getTargetAsMultiAlignment(alignments, target);
                if (helperBig == null)
                    continue;
                for (MultiAlignmentHelper helper : helperBig.split(80)) {
                    System.out.println(helper);
                    System.out.println();
                    if (--k < 0)
                        return;
                }
            }

        }

        //System.out.println("Bytes per alignment: " + (bos.size() - header) / alignemntsList.size());
        //
        //try (VDJCAlignmentsReader reader = new VDJCAlignmentsReader(new ByteArrayInputStream(bos.toByteArray()), ll)) {
        //    int i = 0;
        //    for (VDJCAlignments alignments : CUtils.it(reader))
        //        Assert.assertEquals(alignemntsList.get(i++), alignments);
        //}
    }

    @Test
    @Ignore
    public void test2() throws Exception {

        PairedRead read1 = new PairedRead(
                new SingleReadImpl(0,
                        new NSequenceWithQuality(new NucleotideSequence("GCTGTGTATTACTGTGCAAGAGGGCCCCAAGAAAATAGTGGTTATTACTACGGGTTTGACTACTGGGGCCAGGGA"), SequenceQuality.GOOD_QUALITY_VALUE),
                        "206"),
                new SingleReadImpl(
                        0,
                        new NSequenceWithQuality(new NucleotideSequence("GGCGCCAGGGGGAAGACCGATGGGCCCTTGGTGGAGGCTGAGGAGACGGTGACCAGGGTTCCCTGGCCCCAGTAG"), SequenceQuality.GOOD_QUALITY_VALUE),
                        "206")
        );

        PairedRead read2 = new PairedRead(
                new SingleReadImpl(1,
                        new NSequenceWithQuality(new NucleotideSequence("GCTGTGTATTACTGTGCAAGAGGGCCCCAAGAAAATAGTGGTTATTACTACGGGTTTGACTACTGGGGCCAGGGA"), SequenceQuality.GOOD_QUALITY_VALUE),
                        "11621"),
                new SingleReadImpl(
                        1,
                        new NSequenceWithQuality(new NucleotideSequence("GGCGCCAGGGGGAAGACCGATGGGCCCTTGGTGGAGGCTGAGGAGACGGTGACCAGGGTTCCCTGGCCCCAGTAG"), SequenceQuality.GOOD_QUALITY_VALUE),
                        "11621")
        );


        RunMiXCR.RunMiXCRAnalysis params = new RunMiXCR.RunMiXCRAnalysis(read1);
        RunMiXCR.AlignResult align = RunMiXCR.align(params);

        for (VDJCAlignments al : align.alignments) {
            for (int i = 0; i < al.numberOfTargets(); i++) {
                System.out.println(VDJCAlignmentsFormatter.getTargetAsMultiAlignment(al, i));
                System.out.println();
            }
            System.out.println();
            System.out.println(" ================================================ ");
            System.out.println();
        }
    }
}
