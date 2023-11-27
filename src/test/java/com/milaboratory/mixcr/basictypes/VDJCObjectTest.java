/*
 * Copyright (c) 2014-2023, MiLaboratories Inc. All Rights Reserved
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

import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.mixcr.basictypes.tag.TagsInfo;
import com.milaboratory.mixcr.cli.CommandExportAlignmentsPretty;
import com.milaboratory.mixcr.util.RunMiXCR;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.RelativePointSide;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

import static io.repseq.core.GeneFeature.*;
import static io.repseq.core.ReferencePoint.*;
import static org.junit.Assert.*;

/**
 *
 */
public class VDJCObjectTest {
    @Test
    public void test1() throws Exception {
        for (String name : Arrays.asList("test", "sample_IGH")) {
            RunMiXCR.RunMiXCRAnalysis params = new RunMiXCR.RunMiXCRAnalysis(
                    RunMiXCR.class.getResource("/sequences/" + name + "_R1.fastq").getFile(),
                    RunMiXCR.class.getResource("/sequences/" + name + "_R2.fastq").getFile());

            RunMiXCR.AlignResult align = RunMiXCR.align(params);
            RunMiXCR.AssembleResult assemble = RunMiXCR.assemble(align, false);
            RunMiXCR.FullSeqAssembleResult assembleFullSeq = RunMiXCR.assembleContigs(assemble);

            GeneFeature[] incompleteFeatures = {
                    new GeneFeature(CDR1),
                    new GeneFeature(CDR2),
                    new GeneFeature(CDR3),
                    new GeneFeature(CDR1, CDR2),
                    new GeneFeature(CDR2, CDR3),
                    new GeneFeature(CDR1, CDR2, CDR3),
                    new GeneFeature(CDR1, FR4),
                    new GeneFeature(CDR2, FR4),
                    new GeneFeature(CDR3, FR4),
                    new GeneFeature(CDR1, CDR2, FR4),
                    new GeneFeature(CDR2, CDR3, FR4),
                    new GeneFeature(CDR1, CDR2, CDR3, FR4),
                    VDJRegion,
            };

            // catching exceptions
            for (GeneFeature feature : incompleteFeatures) {
                for (Iterable<? extends VDJCObject> it : Arrays.asList(
                        align.alignments,
                        assemble.cloneSet,
                        assembleFullSeq.cloneSet)) {
                    for (VDJCObject o : it) {
                        VDJCObject.CaseSensitiveNucleotideSequence seq = o.getIncompleteFeature(feature);
                        if (seq == null)
                            continue;

                        if (feature.contains(CDR3)) {
                            assertTrue(seq.containsUpperCase());
                            NSequenceWithQuality cdr3 = o.getFeature(CDR3);
                            if (cdr3 != null)
                                assertTrue(seq.toString().contains(cdr3.getSequence().toString().toUpperCase()));
                        }
                    }
                }
            }
        }
    }

    @Test
    public void test2() throws Exception {
        RunMiXCR.RunMiXCRAnalysis params = new RunMiXCR.RunMiXCRAnalysis(
                RunMiXCR.class.getResource("/sequences/test_R1.fastq").getFile(),
                RunMiXCR.class.getResource("/sequences/test_R2.fastq").getFile());

        RunMiXCR.AlignResult align = RunMiXCR.align(params);
        //new CommandExportAlignmentsPretty().outputCompact(System.out, align.alignments.get(3));

        assertNotNull(align.alignments.get(0).getIncompleteFeature(new GeneFeature(new GeneFeature(FR3Begin, FR3End.move(-10)), CDR3)));
        assertNotNull(align.alignments.get(2).getIncompleteFeature(new GeneFeature(new GeneFeature(FR3Begin, FR3End.move(-10)), CDR3)));
        assertNotNull(align.alignments.get(3).getIncompleteFeature(new GeneFeature(new GeneFeature(FR3Begin, FR3End.move(-10)), CDR3)));
        //new ActionExportAlignmentsPretty().outputCompact(System.out, al);

        new CommandExportAlignmentsPretty().outputCompact(System.out, align.alignments.get(0), TagsInfo.NO_TAGS);
        System.out.println(align.alignments.get(0).getIncompleteFeature(CDR3));
        System.out.println(align.alignments.get(0).getIncompleteFeature(VDJRegion));
    }

    @Test
    public void test3() throws Exception {
        RunMiXCR.RunMiXCRAnalysis params = new RunMiXCR.RunMiXCRAnalysis(
                RunMiXCR.class.getResource("/sequences/test_R1.fastq").getFile(),
                RunMiXCR.class.getResource("/sequences/test_R2.fastq").getFile());

        RunMiXCR.AlignResult align = RunMiXCR.align(params);
        VDJCAlignments al = align.alignments.get(0);
        VDJCObject.CaseSensitiveNucleotideSequence seq = al.getIncompleteFeature(VDJRegion);
        assertTrue(seq.toString().contains(al.getFeature(CDR3).getSequence().toString().toUpperCase()));
    }

    @Test
    public void test4() throws Exception {
        RunMiXCR.RunMiXCRAnalysis params = new RunMiXCR.RunMiXCRAnalysis(
                RunMiXCR.class.getResource("/sequences/test_R1.fastq").getFile(),
                RunMiXCR.class.getResource("/sequences/test_R2.fastq").getFile());

        RunMiXCR.AlignResult align = RunMiXCR.align(params);
        VDJCAlignments al = align.alignments.get(43);
        //new ActionExportAlignmentsPretty().outputCompact(System.out, al);
        assertNotNull(al.getIncompleteFeature(VDJRegion));
        assertNull(al.getFeature(CDR3));
        assertNull(al.getIncompleteFeature(CDR3));
    }

    @Test
    public void test5() throws Exception {
        RunMiXCR.RunMiXCRAnalysis params = new RunMiXCR.RunMiXCRAnalysis(
                RunMiXCR.class.getResource("/sequences/test_R1.fastq").getFile(),
                RunMiXCR.class.getResource("/sequences/test_R2.fastq").getFile());

        RunMiXCR.AlignResult align = RunMiXCR.align(params);
        for (int i = 0; i < align.alignments.size(); ++i) {
            VDJCAlignments al = align.alignments.get(i);
            NSequenceWithQuality cdr3 = al.getFeature(CDR3);
            VDJCObject.CaseSensitiveNucleotideSequence seq = al.getIncompleteFeature(VDJRegion);
            if (cdr3 != null && seq == null) {
                assertTrue(
                        al.getBestHit(GeneType.Variable).getAlignment(0).getSequence2Range().getFrom() != 0
                                || al.getBestHit(GeneType.Joining).getAlignment(1).getSequence2Range().getTo() != al.getTarget(1).size());
                continue;
            }
            if (seq != null && cdr3 != null)
                assertTrue(seq.toString().contains(cdr3.getSequence().toString().toUpperCase()));
        }
    }

    @Test
    public void test6() throws Exception {
        RunMiXCR.RunMiXCRAnalysis params = new RunMiXCR.RunMiXCRAnalysis(
                RunMiXCR.class.getResource("/sequences/test_R1.fastq").getFile(),
                RunMiXCR.class.getResource("/sequences/test_R2.fastq").getFile());

        RunMiXCR.AlignResult align = RunMiXCR.align(params);
        for (int i = 0; i < align.alignments.size(); ++i) {
            VDJCAlignments al = align.alignments.get(i);
            for (int tIdx = 0; tIdx < al.numberOfTargets(); tIdx++) {
                VDJCPartitionedSequence pt = al.getPartitionedTarget(tIdx);
                if (pt == null)
                    continue;
                TargetPartitioning partitioning = pt.getPartitioning();
                if (partitioning.isAvailable(CDR3)) {
                    Assert.assertEquals(RelativePointSide.MatchOrInside,
                            partitioning.getRelativeSide(CDR3Begin));
                    Assert.assertEquals(RelativePointSide.MatchOrInside,
                            partitioning.getRelativeSide(CDR3End));
                    Assert.assertEquals(RelativePointSide.Left,
                            partitioning.getRelativeSide(FR1Begin));
                }
                if (partitioning.isAvailable(FR1Begin)) {
                    Assert.assertEquals(RelativePointSide.Right,
                            partitioning.getRelativeSide(CDR3End));
                    Assert.assertEquals(RelativePointSide.Right,
                            partitioning.getRelativeSide(CDR3Begin));
                }
            }
        }
    }

    @Test
    public void test7() throws Exception {
        RunMiXCR.RunMiXCRAnalysis params = new RunMiXCR.RunMiXCRAnalysis(
                RunMiXCR.class.getResource("/sequences/test_R1.fastq").getFile(),
                RunMiXCR.class.getResource("/sequences/test_R2.fastq").getFile());

        params.alignerParameters.getVAlignerParameters().setGeneFeatureToAlign(VTranscriptWithP);
        RunMiXCR.AlignResult align = RunMiXCR.align(params);
        RunMiXCR.AssembleResult assemble = RunMiXCR.assemble(align);

        for (int i = 0; i < assemble.cloneSet.size(); i++) {

            VDJCObject cl = assemble.cloneSet.get(i);
            VDJCObject.CaseSensitiveNucleotideSequence f = cl.getIncompleteFeature(L2);

            NucleotideSequence lSeq = f.getSeq()[0];
            NucleotideSequence germ = cl.getBestHit(GeneType.Variable).getGene().getFeature(L2);
            assertEquals(germ, lSeq);
        }
    }
}
