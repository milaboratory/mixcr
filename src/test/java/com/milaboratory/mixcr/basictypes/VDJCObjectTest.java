package com.milaboratory.mixcr.basictypes;

import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.mixcr.cli.ActionExportAlignmentsPretty;
import com.milaboratory.mixcr.util.RunMiXCR;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import org.junit.Test;

import java.util.Arrays;

import static io.repseq.core.GeneFeature.*;
import static io.repseq.core.ReferencePoint.FR3Begin;
import static io.repseq.core.ReferencePoint.FR3End;
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
                        assembleFullSeq.cloneSet,
                        assembleFullSeq.cloneSet)) {
                    for (VDJCObject o : it) {
                        VDJCObject.CaseSensitiveNucleotideSequence seq = o.getIncompleteFeature(feature);
                        if (seq == null)
                            continue;

                        if (feature.contains(CDR3)) {
                            assertTrue(seq.containsUpperCase());
                            NSequenceWithQuality cdr3 = o.getFeature(CDR3);
                            assertNotNull(feature.toString(), cdr3);
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
        assertNotNull(align.alignments.get(0).getIncompleteFeature(new GeneFeature(new GeneFeature(FR3Begin, FR3End.move(-10)), CDR3)));
        assertNotNull(align.alignments.get(2).getIncompleteFeature(new GeneFeature(new GeneFeature(FR3Begin, FR3End.move(-10)), CDR3)));
        assertNull(align.alignments.get(3).getIncompleteFeature(new GeneFeature(new GeneFeature(FR3Begin, FR3End.move(-10)), CDR3)));
        //new ActionExportAlignmentsPretty().outputCompact(System.out, al);

        new ActionExportAlignmentsPretty().outputCompact(System.out, align.alignments.get(0));
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
        assertNull(al.getIncompleteFeature(VDJRegion));
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
            assertTrue((cdr3 == null) == (seq == null));
            if (seq != null)
                assertTrue(seq.toString().contains(al.getFeature(CDR3).getSequence().toString().toUpperCase()));
        }
    }
}