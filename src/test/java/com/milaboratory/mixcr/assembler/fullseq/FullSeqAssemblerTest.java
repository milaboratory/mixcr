package com.milaboratory.mixcr.assembler.fullseq;

import cc.redberry.pipe.CUtils;
import com.milaboratory.core.io.sequence.PairedRead;
import com.milaboratory.core.io.sequence.SequenceRead;
import com.milaboratory.core.io.sequence.SequenceReaderCloseable;
import com.milaboratory.core.io.sequence.SingleReadImpl;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceQuality;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.CloneSet;
import com.milaboratory.mixcr.basictypes.VDJCAlignments;
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsFormatter;
import com.milaboratory.mixcr.cli.ActionExportClonesPretty;
import com.milaboratory.mixcr.cli.Main;
import com.milaboratory.mixcr.util.RunMiXCR;
import com.milaboratory.mixcr.vdjaligners.VDJCParametersPresets;
import gnu.trove.set.hash.TIntHashSet;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.Well19937c;
import org.apache.commons.math3.random.Well44497b;
import org.junit.Assert;
import org.junit.Test;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 *
 */
public class FullSeqAssemblerTest {
    static final FullSeqAssemblerParameters DEFAULT_PARAMETERS =
            new FullSeqAssemblerParameters(0.1, 80, 120,
                    3, 7, 0.25, GeneFeature.VDJRegion);

    static final class MasterSequence {
        final int vPart, cdr3Part, jPart, cPart;
        final NucleotideSequence masterSequence;

        MasterSequence(NucleotideSequence vPart, NucleotideSequence cdr3Part, NucleotideSequence jPart, NucleotideSequence cPart) {
            this.vPart = vPart.size();
            this.cdr3Part = cdr3Part.size();
            this.jPart = jPart.size();
            this.cPart = cPart.size();
            this.masterSequence = vPart.concatenate(cdr3Part).concatenate(jPart).concatenate(cPart);
        }

        MasterSequence(String vPart, String cdr3Part, String jPart, String cPart) {
            this(new NucleotideSequence(vPart.replace(" ", "")), new NucleotideSequence(cdr3Part.replace(" ", "")),
                    new NucleotideSequence(jPart.replace(" ", "")), new NucleotideSequence(cPart.replace(" ", "")));
        }

        NucleotideSequence getRange(int vPadd, int jPadd) {
            return masterSequence.getRange(vPart + vPadd, vPart + cdr3Part + jPadd);
        }

        NucleotideSequence getRangeFromCDR3Begin(int vPadd, int len) {
            return masterSequence.getRange(vPart + vPadd, vPart + vPadd + len);
        }

        NucleotideSequence getRangeFromCDR3End(int jPadd, int len) {
            return masterSequence.getRange(vPart + cdr3Part + jPadd, vPart + cdr3Part + jPadd + len);
        }
    }

    static final MasterSequence masterSeq1WT = new MasterSequence(
            "CTGAAGAAAACCAGCCCTGCAGCTCTGGGAGAGGAGCCCCAGCCCTGGGATTCCCAGCTGTTTCTGCTTGCTGATCAGGACTGCACACAGAGAACTCACC" +
                    "ATGGAGTTTGGGCTGAGCTGGGTTTTCCTTGTTGCTATTTTAAAAGGTGTCCAGTGTGAGGTGCAGCTGGTGGAGTCCGGGGGAGGCTTAGTTCAGCC" +
                    "TGGGGGGTCCCTGAGACTCTCCTGTGCAGCCTCTGGATTCACCTTCAGTAGCTACTGGATGCACTGGGTCCGCCAAGCTCCAGGGAAGGGGCTGGTGT" +
                    "GGGTCTCACGTATTAATAGTGATGGGAGTAGCACAAGCTACGCGGACTCCGTGAAGGGCCGATTCACCATCTCCAGAGACAACGCCAAGAACACGCTG" +
                    "TATCTGCAAATGAACAGTCTGAGAGCCGAGGACACGGCTGTGTATTAC",
            "TGTGCAAGAGGGCCCCAAGAAAATAGTGGTTATTACTACGGGTTTGACTACTGG",
            "GGCCAGGGAACCCTGGTCACCGTCTCCTCAG",
            "CCTCCACCAAGGGCCCATCGGTCTTCCCCCTGGCGCCCTGCTCCAGGAGCACCTCCGAGAGCACAGCGGCCCTGGGCTGCCTGGTCAAGGACTACTTCCC" +
                    "CGAACCGGTGACGGTGTCGTGGAACTCAGGCGCTCTGACCAGCGGCGTGCACACCTTCCCGGCTGTCCTACAGTCCTCAGGACTCTACTCCCTCAGCA" +
                    "GCGTGGTGACCGTGCCCTCCAGCAACTTCGGCACCCAGACCTACACCTGCAACGTAGATCACAAGCCCAGCAACACCAAGGTGGACAAGACAGTTGGT" +
                    "GAGAGGCCAGCTCAGGGAGGGAGGGTGTCTGCTGGAAGCCAGGCTCAGCCCTCCTGCCTGGACGCACCCCGGCTGTGCAGCCCCAGCCCAGGGCAGCA" +
                    "AGGCAGGCCCCATCTGTCTCCTCACCCGGAGGCCTCTGCCCGCCCCACTCATGCTCAGGGAGAGGGTCTTCTGGCTTTTTCCACCAGGCTCCAGGCAG" +
                    "GCACAGGCTGGGTGCCCCTACCCCAGGCCCTTCACACACAGGGGCAGGTGCTTGGCTCAGACCTGCCAAAAGCCATATCCGG");

    static final MasterSequence masterSeq1VDel1JDel1 = new MasterSequence(
            "CTGAAGAAAACCAGCCCTGCAGCTCTGGGAGAGGAGCCCCAGCCCTGGGATTCCCAGCTGTTTCTGCTTGCTGATCAGGACTGCACACAGAGAACTCACC" +
                    "ATGGAGTTTGGGCTGAGCTGGGTTTTCCTTGTTGCTATTTTAAAAGGTGTCCAGTGTGAGGTGCAGCTGGTGGAGTCCGGGGGAGGCTTAGTTCAGCC" +
                    "TGGGGGGTCCCTGAGACTCTCCTGTGCAGCCTCTGGATTCACCTTCAGTAGCTACTGGATGCACTGGGTCCGCCAAGCTCCAGGGAAGGGGCTGGTGT" +
                    "GGGTCTCACGTATTAATAGTGATGGGAGTAGCACAAGCTACGCGGACTCCGTGAAGGGCCGATTCACCATCTCCAGAACAACGCCAAGAACACGCTG" +
                    "TATCTGCAAATGAACAGTCTGAGAGCCGAGGACACGGCTGTGTATTAC",
            "TGTGCAAGAGGGCCCCAAGAAAATAGTGGTTATTACTACGGGTTTGACTACTGG",
            "GGCCAGGGAACCCTGGTC CCGTCTCCTCAG",
            "CCTCCACCAAGGGCCCATCGGTCTTCCCCCTGGCGCCCTGCTCCAGGAGCACCTCCGAGAGCACAGCGGCCCTGGGCTGCCTGGTCAAGGACTACTTCCC" +
                    "CGAACCGGTGACGGTGTCGTGGAACTCAGGCGCTCTGACCAGCGGCGTGCACACCTTCCCGGCTGTCCTACAGTCCTCAGGACTCTACTCCCTCAGCA" +
                    "GCGTGGTGACCGTGCCCTCCAGCAACTTCGGCACCCAGACCTACACCTGCAACGTAGATCACAAGCCCAGCAACACCAAGGTGGACAAGACAGTTGGT" +
                    "GAGAGGCCAGCTCAGGGAGGGAGGGTGTCTGCTGGAAGCCAGGCTCAGCCCTCCTGCCTGGACGCACCCCGGCTGTGCAGCCCCAGCCCAGGGCAGCA" +
                    "AGGCAGGCCCCATCTGTCTCCTCACCCGGAGGCCTCTGCCCGCCCCACTCATGCTCAGGGAGAGGGTCTTCTGGCTTTTTCCACCAGGCTCCAGGCAG" +
                    "GCACAGGCTGGGTGCCCCTACCCCAGGCCCTTCACACACAGGGGCAGGTGCTTGGCTCAGACCTGCCAAAAGCCATATCCGG");

    static final MasterSequence masterSeq1VDel1JDelVSub2 = new MasterSequence(
            "CTGAAGAAAACCAGCCCTGCAGCTCTGGGAGAGGAGCCCCAGCCCTGGGATTCCCAGCTGTTTCTGCTTGCTGATCAGGACTGCACACAGAGAACTCACC" +
                    "ATGGAGTTTGGGCTGAGCTGGGTTTTCCTTGTTGCTATTTTAAAAGGTGTCCAGTGTGAGGTGCAGCTGGTGGAGTCCGGGGGAGGCTTAGTTCAGCC" +
                    "TGGGGGGTCCCTGAGACTCTCCTGTGCAGCCTCTGGATTCACCTTCAGTAGCTACTGGATGCACTGGGTCCGCCAAGCTCCAGGGAAGGGGCTGGTGT" +
                    "GGGTCTCACGTATTAATAGTGATtGGAGTAGCACAAGCTACGCGGACTCCGTGAAGGGCCGtTTCACCATCTCCAGAGACAACGCCAAGAACACGTG" +
                    "TATCTGCAAATGAACAGTCTGAGAGCCGAGGACACGGCTGTGTATTAC",
            "TGTGCAAGAGGGCCCCAAGAAAATAGTGGTTATTACTACGGGTTTGACTACTGG",
            "GGCCAGGGAACCCTGGTCACCGTCTCTCAG",
            "CCTCCACCAAGGGCCCATCGGTCTTCCCCCTGGCGCCCTGCTCCAGGAGCACCTCCGAGAGCACAGCGGCCCTGGGCTGCCTGGTCAAGGACTACTTCCC" +
                    "CGAACCGGTGACGGTGTCGTGGAACTCAGGCGCTCTGACCAGCGGCGTGCACACCTTCCCGGCTGTCCTACAGTCCTCAGGACTCTACTCCCTCAGCA" +
                    "GCGTGGTGACCGTGCCCTCCAGCAACTTCGGCACCCAGACCTACACCTGCAACGTAGATCACAAGCCCAGCAACACCAAGGTGGACAAGACAGTTGGT" +
                    "GAGAGGCCAGCTCAGGGAGGGAGGGTGTCTGCTGGAAGCCAGGCTCAGCCCTCCTGCCTGGACGCACCCCGGCTGTGCAGCCCCAGCCCAGGGCAGCA" +
                    "AGGCAGGCCCCATCTGTCTCCTCACCCGGAGGCCTCTGCCCGCCCCACTCATGCTCAGGGAGAGGGTCTTCTGGCTTTTTCCACCAGGCTCCAGGCAG" +
                    "GCACAGGCTGGGTGCCCCTACCCCAGGCCCTTCACACACAGGGGCAGGTGCTTGGCTCAGACCTGCCAAAAGCCATATCCGG");


    static final MasterSequence masterSeq1VSub1 = new MasterSequence(
            "CTGAAGAAAACCAGCCCTGCAGCTCTGGGAGAGGAGCCCCAGCCCTGGGATTCCCAGCTGTTTCTGCTTGCTGATCAGGACTGCACACAGAGAACTCACC" +
                    "ATGGAGTTTGGGCTGAGCTGGGTTTTCCTTGTTGCTATTTTAAAAGGTGTCCAGTGTGAGGTGCAGCTGGTGGAGTCCGGGGGAGGCTTAGTTCAGCC" +
                    "TGGGGGGTCCCTGAGACTCTCCTGTGCAGCCTCTGGATTCACCTTCAGTAGCTACTGGATGCACTGGGTCCGCCAAGCTCCAGGGAAGGGGCTGGTGT" +
                    "GGGTCTCACGTATTAATAGTGATGGGAGTAGCACAAGCTACGCGGACTCCtTGAAGGGCCGATTCACCATCTCCAGAGACAACGCCAAGAACACGCTG" +
                    "TATCTGCAAATGAACAGTCTGAGAGCCGAGGACACGGCTGTGTATTAC",
            "TGTGCAAGAGGGCCCCAAGAAAATAGTGGTTATTACTACGGGTTTGACTACTGG",
            "GGCCAGGGAACCCTGGTCACCGTCTCCTCAG",
            "CCTCCACCAAGGGCCCATCGGTCTTCCCCCTGGCGCCCTGCTCCAGGAGCACCTCCGAGAGCACAGCGGCCCTGGGCTGCCTGGTCAAGGACTACTTCCC" +
                    "CGAACCGGTGACGGTGTCGTGGAACTCAGGCGCTCTGACCAGCGGCGTGCACACCTTCCCGGCTGTCCTACAGTCCTCAGGACTCTACTCCCTCAGCA" +
                    "GCGTGGTGACCGTGCCCTCCAGCAACTTCGGCACCCAGACCTACACCTGCAACGTAGATCACAAGCCCAGCAACACCAAGGTGGACAAGACAGTTGGT" +
                    "GAGAGGCCAGCTCAGGGAGGGAGGGTGTCTGCTGGAAGCCAGGCTCAGCCCTCCTGCCTGGACGCACCCCGGCTGTGCAGCCCCAGCCCAGGGCAGCA" +
                    "AGGCAGGCCCCATCTGTCTCCTCACCCGGAGGCCTCTGCCCGCCCCACTCATGCTCAGGGAGAGGGTCTTCTGGCTTTTTCCACCAGGCTCCAGGCAG" +
                    "GCACAGGCTGGGTGCCCCTACCCCAGGCCCTTCACACACAGGGGCAGGTGCTTGGCTCAGACCTGCCAAAAGCCATATCCGG");


    static final MasterSequence masterSeq1VLargeIns1 = new MasterSequence(
            "CTGAAGAAAACCAGCCCTGCAGCTCTGGGAGAGGAGCCCCAGCCCTGGGATTCCCAGCTGTTTCTGCTTGCTGATCAGGACTGCACACAGAGAACTCACC" +
                    "ATGGAGTTTGGGCTGAGCTGGGTTTTCCTTGTTGCTATTTTAAAAGGTGTCCAGTGTGAGGTGCAGCTGGTGGAGTCCGGGGGAGGCTTAGTTCAGCC" +
                    "TGGGGGGTCCCTGAGACTCTCCTGTGCAGCCTCTGGATTCACCTTCAGTAGCTACTGGATGCACTGGGTCCGCCAAGCTCCAGGGAAGGGGCTGGTGT" +
                    "GGGTCTCACGTATTAATAGTGATGGGAGTAGCACAAGCTtattACGCGGACTCCGTGAAGGGCCGATTCACCATCTCCAGAGACAACGCCAAGAACACGCTG" +
                    "TATCTGCAAATGAACAGTCTGAGAGCCGAGGACACGGCTGTGTATTAC",
            "TGTGCAAGAGGGCCCCAAGAAAATAGTGGTTATTACTACGGGTTTGACTACTGG",
            "GGCCAGGGAACCCTGGTCACCGTCTCCTCAG",
            "CCTCCACCAAGGGCCCATCGGTCTTCCCCCTGGCGCCCTGCTCCAGGAGCACCTCCGAGAGCACAGCGGCCCTGGGCTGCCTGGTCAAGGACTACTTCCC" +
                    "CGAACCGGTGACGGTGTCGTGGAACTCAGGCGCTCTGACCAGCGGCGTGCACACCTTCCCGGCTGTCCTACAGTCCTCAGGACTCTACTCCCTCAGCA" +
                    "GCGTGGTGACCGTGCCCTCCAGCAACTTCGGCACCCAGACCTACACCTGCAACGTAGATCACAAGCCCAGCAACACCAAGGTGGACAAGACAGTTGGT" +
                    "GAGAGGCCAGCTCAGGGAGGGAGGGTGTCTGCTGGAAGCCAGGCTCAGCCCTCCTGCCTGGACGCACCCCGGCTGTGCAGCCCCAGCCCAGGGCAGCA" +
                    "AGGCAGGCCCCATCTGTCTCCTCACCCGGAGGCCTCTGCCCGCCCCACTCATGCTCAGGGAGAGGGTCTTCTGGCTTTTTCCACCAGGCTCCAGGCAG" +
                    "GCACAGGCTGGGTGCCCCTACCCCAGGCCCTTCACACACAGGGGCAGGTGCTTGGCTCAGACCTGCCAAAAGCCATATCCGG");


    static final MasterSequence masterSeq1VLargeIns1Sub1 = new MasterSequence(
            "CTGAAGAAAACCAGCCCTGCAGCTCTGGGAGAGGAGCCCCAGCCCTGGGATTCCCAGCTGTTTCTGCTTGCTGATCAGGACTGCACACAGAGAACTCACC" +
                    "ATGGAGTTTGGGCTGAGCTGGGTTTTCCTTGTTGCTATTTTAAAAGGTGTCCAGTGTGAGGTGCAGCTGGTGGAGTCCGGGGGAGGCTTAGTTCAGCC" +
                    "TGGGGGGTCCCTGAGACTCTCCTGTGCAGCCTCTGGATTCACCTTCAGTAGCTACTGGATGCACTGGGTCCGCCAAGCTCCAGGGAAGGGGCTGGTGT" +
                    "GGGTCTCACGTATTAATAGTGATGGGAGTAGCACAAGCTtattACGCGGACTCCGTGAAGGGCCGATTCACCATCTCCAGAGACAACGCCAAGAACACGCTG" +
                    "TATCTGCAAATGAACAGTCTcAGAGCCGAGGACACGGCTGTGTATTAC",
            "TGTGCAAGAGGGCCCCAAGAAAATAGTGGTTATTACTACGGGTTTGACTACTGG",
            "GGCCAGGGAACCCTGGTCACCGTCTCCTCAG",
            "CCTCCACCAAGGGCCCATCGGTCTTCCCCCTGGCGCCCTGCTCCAGGAGCACCTCCGAGAGCACAGCGGCCCTGGGCTGCCTGGTCAAGGACTACTTCCC" +
                    "CGAACCGGTGACGGTGTCGTGGAACTCAGGCGCTCTGACCAGCGGCGTGCACACCTTCCCGGCTGTCCTACAGTCCTCAGGACTCTACTCCCTCAGCA" +
                    "GCGTGGTGACCGTGCCCTCCAGCAACTTCGGCACCCAGACCTACACCTGCAACGTAGATCACAAGCCCAGCAACACCAAGGTGGACAAGACAGTTGGT" +
                    "GAGAGGCCAGCTCAGGGAGGGAGGGTGTCTGCTGGAAGCCAGGCTCAGCCCTCCTGCCTGGACGCACCCCGGCTGTGCAGCCCCAGCCCAGGGCAGCA" +
                    "AGGCAGGCCCCATCTGTCTCCTCACCCGGAGGCCTCTGCCCGCCCCACTCATGCTCAGGGAGAGGGTCTTCTGGCTTTTTCCACCAGGCTCCAGGCAG" +
                    "GCACAGGCTGGGTGCCCCTACCCCAGGCCCTTCACACACAGGGGCAGGTGCTTGGCTCAGACCTGCCAAAAGCCATATCCGG");

    @Test
    public void testRandom1() throws Exception {
        CloneFraction[] clones = {
                new CloneFraction(750, masterSeq1WT),
                //V: S346:G->T
                new CloneFraction(1000, masterSeq1VSub1),
                //V: D373:G
                //J: D55:A
                new CloneFraction(1000, masterSeq1VDel1JDel1),
                //V: S319:G->T,S357:A->T,D391:C
                //J: D62:C
                new CloneFraction(500, masterSeq1VDel1JDelVSub2),
        };

        Well19937c rand = new Well19937c();
        rand.setSeed(12345);
        RandomDataGenerator rdg = new RandomDataGenerator(rand);

        List<SequenceRead> readsOrig = new ArrayList<>();

        int readLength = 100;

        int id = -1;

        for (CloneFraction clone : clones) {
            for (int i = 0; i < clone.count; i++) {
                // Left read with CDR3
                ++id;
                readsOrig.add(new PairedRead(
                        new SingleReadImpl(id, new NSequenceWithQuality(clone.seq.getRangeFromCDR3Begin(-rand.nextInt(readLength - clone.seq.cdr3Part), readLength)), "R1_" + id),
                        new SingleReadImpl(id, new NSequenceWithQuality(clone.seq.getRangeFromCDR3End(rdg.nextInt(-clone.seq.cdr3Part / 2, clone.seq.jPart),
                                readLength).getReverseComplement()), "R2_" + id)));

                ++id;
                readsOrig.add(new PairedRead(
                        new SingleReadImpl(id, new NSequenceWithQuality(clone.seq.getRangeFromCDR3Begin(rdg.nextInt(-clone.seq.vPart, clone.seq.cdr3Part / 2 - readLength), readLength)), "R1_" + id),
                        new SingleReadImpl(id, new NSequenceWithQuality(clone.seq.getRangeFromCDR3Begin(-rand.nextInt(readLength - clone.seq.cdr3Part),
                                readLength)).getReverseComplement(), "R2_" + id)));
            }
        }

//        readsOrig = Arrays.asList(setReadId(0, readsOrig.get(12)), setReadId(1, readsOrig.get(13)));

        int[] perm = rdg.nextPermutation(readsOrig.size(), readsOrig.size());
        List<SequenceRead> reads = new ArrayList<>();
        for (int i = 0; i < readsOrig.size(); i++)
            reads.add(readsOrig.get(perm[i]));

        RunMiXCR.RunMiXCRAnalysis params = new RunMiXCR.RunMiXCRAnalysis(
                new SequenceReaderCloseable<SequenceRead>() {
                    int counter = 0;

                    @Override
                    public void close() {
                    }

                    @Override
                    public long getNumberOfReads() {
                        return counter;
                    }

                    @Override
                    public synchronized SequenceRead take() {
                        if (counter == reads.size())
                            return null;
                        return reads.get(counter++);
                    }
                }, true);

        params.alignerParameters = VDJCParametersPresets.getByName("rna-seq");
        params.alignerParameters.setSaveOriginalReads(true);
        params.alignerParameters.setVAlignmentParameters(params.alignerParameters.getVAlignerParameters().setGeneFeatureToAlign(GeneFeature.VTranscriptWithP));

        RunMiXCR.AlignResult align = RunMiXCR.align(params);

//        // TODO exception for translation
//        for (VDJCAlignments al : align.alignments) {
//            for (int i = 0; i < al.numberOfTargets(); i++) {
//                System.out.println(VDJCAlignmentsFormatter.getTargetAsMultiAlignment(al, i));
//                System.out.println();
//            }
//            System.out.println();
//            System.out.println(" ================================================ ");
//            System.out.println();
//        }


        RunMiXCR.AssembleResult assemble = RunMiXCR.assemble(align);
        Assert.assertEquals(1, assemble.cloneSet.size());

        FullSeqAssembler agg = new FullSeqAssembler(DEFAULT_PARAMETERS, assemble.cloneSet.get(0), align.parameters.alignerParameters);

        FullSeqAssembler.RawVariantsData prep = agg.calculateRawData(() -> CUtils.asOutputPort(
                align.alignments.stream().filter(a -> a.getFeature(GeneFeature.CDR3) != null).collect(Collectors.toList())
        ));

        List<Clone> clns = new ArrayList<>(new CloneSet(Arrays.asList(agg.callVariants(prep))).getClones());
        clns.sort(Comparator.comparingDouble(Clone::getCount).reversed());

        System.out.println("# Clones: " + clns.size());
        id = 0;
        for (Clone clone : clns) {
            clone = clone.setId(id++);
            System.out.println(clone.numberOfTargets());
            System.out.println(clone.getCount());
            System.out.println(clone.getFraction());
            System.out.println(clone.getBestHit(GeneType.Variable).getAlignment(0).getAbsoluteMutations());
            System.out.println(clone.getBestHit(GeneType.Joining).getAlignment(0).getAbsoluteMutations());
            System.out.println();
//            ActionExportClonesPretty.outputCompact(System.out, clone);
        }
    }

    public static class CloneFraction {
        final int count;
        final MasterSequence seq;

        public CloneFraction(int count, MasterSequence seq) {
            this.count = count;
            this.seq = seq;
        }
    }

    @Test
    public void test1() throws Exception {
        int len = 140;
        PairedRead read1 = new PairedRead(
                new SingleReadImpl(0, new NSequenceWithQuality(masterSeq1WT.getRangeFromCDR3Begin(-20, len)), "R1"),
                new SingleReadImpl(0, new NSequenceWithQuality(masterSeq1WT.getRangeFromCDR3Begin(-200, len).getReverseComplement()), "R2"));

        PairedRead read2 = new PairedRead(
                new SingleReadImpl(1, new NSequenceWithQuality(masterSeq1WT.getRangeFromCDR3Begin(-30, len)), "R1"),
                new SingleReadImpl(1, new NSequenceWithQuality(masterSeq1WT.getRangeFromCDR3Begin(-150, len).getReverseComplement()), "R2"));

        RunMiXCR.RunMiXCRAnalysis params = new RunMiXCR.RunMiXCRAnalysis(read1, read2);

        // [-200, -60]  [-20, 120]
        //      [-150, 110]
        //
        // [-200, -150], [110, 120] = 60
        // [-60, -20] = 40
        params.alignerParameters = VDJCParametersPresets.getByName("rna-seq");
        params.alignerParameters.setSaveOriginalReads(true);

        RunMiXCR.AlignResult align = RunMiXCR.align(params);
//        for (VDJCAlignments al : align.alignments) {
//            for (int i = 0; i < al.numberOfTargets(); i++)
//                System.out.println(VDJCAlignmentsFormatter.getTargetAsMultiAlignment(al, i));
//            System.out.println();
//        }

        RunMiXCR.AssembleResult assemble = RunMiXCR.assemble(align);

        FullSeqAssembler agg = new FullSeqAssembler(DEFAULT_PARAMETERS, assemble.cloneSet.get(0), align.parameters.alignerParameters);

        PointSequence[] r2s = agg.toPointSequences(align.alignments.get(1));
        TIntHashSet p2 = new TIntHashSet(Arrays.stream(r2s).mapToInt(s -> s.point).toArray());
        Assert.assertEquals(260 - masterSeq1WT.cdr3Part, p2.size());

        PointSequence[] r1s = agg.toPointSequences(align.alignments.get(0));
        TIntHashSet p1 = new TIntHashSet(Arrays.stream(r1s).mapToInt(s -> s.point).toArray());
        Assert.assertEquals(280 - masterSeq1WT.cdr3Part, p1.size());

        FullSeqAssembler.RawVariantsData prep = agg.calculateRawData(() -> CUtils.asOutputPort(align.alignments));

        long uniq1 = StreamSupport.stream(CUtils.it(prep.createPort()).spliterator(), false)
                .mapToInt(l -> l[0])
                .filter(c -> c == 0xFFFFFFFF).count();
        long uniq2 = StreamSupport.stream(CUtils.it(prep.createPort()).spliterator(), false)
                .mapToInt(l -> l[1])
                .filter(c -> c == 0xFFFFFFFF).count();

        Assert.assertEquals(40, uniq1);
        Assert.assertEquals(60, uniq2);

        for (Clone clone : new CloneSet(Arrays.asList(agg.callVariants(prep))).getClones()) {
            ActionExportClonesPretty.outputCompact(System.out, clone);
            System.out.println();
            System.out.println(" ================================================ ");
            System.out.println();
        }
    }

    @Test
    public void testLargeCloneNoMismatches() throws Exception {
        MasterSequence master = FullSeqAssemblerTest.masterSeq1WT;

        NSequenceWithQuality
                seq = new NSequenceWithQuality(
                master.getRange(-master.vPart + 10, 80),
                SequenceQuality.GOOD_QUALITY_VALUE);

        RunMiXCR.RunMiXCRAnalysis params0 = new RunMiXCR.RunMiXCRAnalysis(new SingleReadImpl(0, seq, ""));
        params0.cloneAssemblerParameters.setAssemblingFeatures(new GeneFeature[]{GeneFeature.VDJRegion});
        Clone largeClone = RunMiXCR.assemble(RunMiXCR.align(params0)).cloneSet.get(0);

//        ActionExportClonesPretty.outputCompact(System.out, largeClone);
//        System.exit(0);

        Well44497b rnd = new Well44497b(1234567889L);
        int nReads = 100_000;
        int readLength = 75, readGap = 150;

        // slice seq randomly
        PairedRead[] slicedReads = new PairedRead[nReads];
        for (int i = 0; i < nReads; ++i) {
            int
                    r1from = rnd.nextInt(seq.size() - readLength - 1),
                    r1to = r1from + readLength,
                    r2from = r1from + 1 + rnd.nextInt(seq.size() - r1from - readLength - 1),
                    r2to = r2from + readLength;

            assert r2from > r1from;
            slicedReads[i] = new PairedRead(
                    new SingleReadImpl(i, seq.getRange(r1from, r1to), "" + i),
                    new SingleReadImpl(i, seq.getRange(r2from, r2to).getReverseComplement(), "" + i));
        }


        RunMiXCR.RunMiXCRAnalysis params = new RunMiXCR.RunMiXCRAnalysis(slicedReads);
//        params.alignerParameters = VDJCParametersPresets.getByName("rna-seq");

        params.alignerParameters.setSaveOriginalReads(true);

        RunMiXCR.AlignResult align = RunMiXCR.align(params);
        RunMiXCR.AssembleResult assemble = RunMiXCR.assemble(align);

//        for (VDJCAlignments al : align.alignments) {
//            for (int i = 0; i < al.numberOfTargets(); i++) {
//                System.out.println(VDJCAlignmentsFormatter.getTargetAsMultiAlignment(al, i));
//                System.out.println();
//            }
//            System.out.println();
//            System.out.println(" ================================================ ");
//            System.out.println();
//        }


        Assert.assertEquals(1, assemble.cloneSet.size());

        Clone initialClone = assemble.cloneSet.get(0);
        NSequenceWithQuality cdr3 = initialClone.getFeature(GeneFeature.CDR3);
        List<VDJCAlignments> alignments = align.alignments.stream()
                .filter(al -> cdr3.equals(al.getFeature(GeneFeature.CDR3)))
                .collect(Collectors.toList());

        alignments.stream().filter(al ->
                Arrays.stream(al.getBestHit(GeneType.Variable).getAlignments())
                        .filter(Objects::nonNull)
                        .anyMatch(a -> !a.getAbsoluteMutations().isEmpty()))
                .filter(al -> al.getBestHit(GeneType.Variable).getGene().getName().contains("3-74"))
                .forEach(al -> {
                    for (int i = 0; i < al.numberOfTargets(); i++) {
                        System.out.println(VDJCAlignmentsFormatter.getTargetAsMultiAlignment(al, i));
                        System.out.println();
                    }
                    System.out.println();
                    System.out.println(" ================================================ ");
                    System.out.println();
                });

//        System.exit(0);
        System.out.println("=> Agg");
        FullSeqAssembler agg = new FullSeqAssembler(DEFAULT_PARAMETERS, initialClone, align.parameters.alignerParameters);
        FullSeqAssembler.RawVariantsData prep = agg.calculateRawData(() -> CUtils.asOutputPort(alignments));
        List<Clone> clones = new ArrayList<>(new CloneSet(Arrays.asList(agg.callVariants(prep))).getClones());
        clones.sort(Comparator.comparingDouble(Clone::getCount).reversed());
        for (Clone clone : clones) {
            ActionExportClonesPretty.outputCompact(System.out, clone);
            System.out.println();
            System.out.println(" ================================================ ");
            System.out.println();
        }
    }

    @Test
    public void test2() throws Exception {
        Main.main("assembleContigs", "-f", "/Users/poslavskysv/Projects/milab/temp/hui.clna", "/Users/poslavskysv/Projects/milab/temp/hui2.clns");
    }

}