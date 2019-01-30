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

import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.mixcr.cli.CommandExportAlignmentsPretty;
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
        assertNotNull(align.alignments.get(0).getIncompleteFeature(new GeneFeature(new GeneFeature(FR3Begin, FR3End.move(-10)), CDR3)));
        assertNotNull(align.alignments.get(2).getIncompleteFeature(new GeneFeature(new GeneFeature(FR3Begin, FR3End.move(-10)), CDR3)));
        assertNull(align.alignments.get(3).getIncompleteFeature(new GeneFeature(new GeneFeature(FR3Begin, FR3End.move(-10)), CDR3)));
        //new ActionExportAlignmentsPretty().outputCompact(System.out, al);

        new CommandExportAlignmentsPretty().outputCompact(System.out, align.alignments.get(0));
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
}