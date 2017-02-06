package com.milaboratory.mixcr.partialassembler;

import com.milaboratory.core.io.sequence.PairedRead;
import com.milaboratory.core.io.sequence.SingleReadImpl;
import com.milaboratory.core.sequence.NSequenceWithQuality;
import com.milaboratory.core.sequence.NucleotideSequence;
import com.milaboratory.core.sequence.SequenceBuilder;
import com.milaboratory.core.sequence.SequenceQuality;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.tests.MiXCRTestUtils;
import com.milaboratory.mixcr.util.RunMiXCR;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.mixcr.vdjaligners.VDJCParametersPresets;
import com.milaboratory.test.TestUtil;
import com.milaboratory.util.RandomUtil;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCLibraryRegistry;
import org.apache.commons.math3.random.RandomGenerator;
import org.junit.Assert;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.EnumMap;

import static io.repseq.core.GeneFeature.*;
import static io.repseq.core.GeneType.*;

public class PartialAlignmentsAssemblerTest {

    static PairedRead createPair(long id, String R1, String R2) {
        return createPair(id, new NucleotideSequence(R1), new NucleotideSequence(R2));
    }

    static PairedRead createPair(long id, NucleotideSequence R1, NucleotideSequence R2) {
        return new PairedRead(
                new SingleReadImpl(id, new NSequenceWithQuality(R1, SequenceQuality.getUniformQuality((byte) 25, R1.size())), "" + id + "R1"),
                new SingleReadImpl(id, new NSequenceWithQuality(R2, SequenceQuality.getUniformQuality((byte) 25, R2.size())), "" + id + "R2"));
    }

    //@Test
    //public void testMaxAllele() throws Exception {
    //    final LociLibrary ll = LociLibraryManager.getDefault().getLibrary("mi");
    //    final Locus locus = Locus.TRB;
    //
    //    for (GeneFeature feature : new GeneFeature[]{VRegionWithP, DRegion, JRegionWithP, CRegion}) {
    //        Allele maxAllele = null;
    //        for (Allele allele : ll.getAllAlleles()) {
    //            if (allele.getLocusContainer().getSpeciesAndLocus().taxonId != Species.HomoSapiens)
    //                continue;
    //            if (!allele.isFunctional())
    //                continue;
    //            if (allele.getLocus() != locus)
    //                continue;
    //            if (maxAllele == null && allele.getFeature(feature) != null)
    //                maxAllele = allele;
    //            if (allele.getFeature(feature) != null && allele.getFeature(feature).size() > maxAllele.getFeature(feature).size())
    //                maxAllele = allele;
    //        }
    //
    //        System.out.println(maxAllele.getName() + "    Size: " + maxAllele.getFeature(feature).size());
    //    }
    //}


    @Test
    public void test1() throws Exception {
        final InputTestData input = createTestData();
        final NucleotideSequence reference = input.reference;
        final EnumMap<GeneType, int[]> refPositions = input.refPositions;
        PairedRead[] data = {
                createPair(0, reference.getRange(refPositions.get(Variable)[1] - 85, refPositions.get(Variable)[1] + 15), reference.getRange(refPositions.get(Joining)[1] - 20, refPositions.get(Joining)[1] + 80).getReverseComplement()),
                createPair(1, reference.getRange(refPositions.get(Variable)[1] - 186, refPositions.get(Variable)[1] - 86), reference.getRange(refPositions.get(Variable)[1] - 10, refPositions.get(Variable)[1] + 102).getReverseComplement())
        };

        final TestResult testResult = processData(data, input);

        for (VDJCAlignments al : testResult.assembled) {
            MiXCRTestUtils.printAlignment(al);
        }
    }


    @Test
    public void test2() throws Exception {
        RandomUtil.reseedThreadLocal(47);
        final InputTestData input = createTestData(47);
        final NucleotideSequence reference = input.reference;
        final EnumMap<GeneType, int[]> refPositions = input.refPositions;
        PairedRead[] data = {
                createPair(0, reference.getRange(refPositions.get(Diversity)[0] - 85, refPositions.get(Diversity)[0] + 10), reference.getRange(refPositions.get(Diversity)[1], refPositions.get(Diversity)[1] + 85).getReverseComplement()),
                createPair(1, reference.getRange(refPositions.get(Diversity)[0] - 135, refPositions.get(Diversity)[0] - 70), reference.getRange(refPositions.get(Diversity)[0] - 8, refPositions.get(Diversity)[0] + 85).getReverseComplement())
        };

        final TestResult testResult = processData(data, input);
        for (VDJCAlignments al : testResult.assembled) {
            MiXCRTestUtils.printAlignment(al);
//            System.out.println(input.VJJunction);
//            System.out.println(al.getFeature(GeneFeature.VJJunction).getSequence());
            Assert.assertTrue(input.VJJunction.toString().contains(al.getFeature(GeneFeature.VJJunction).getSequence().toString()));
        }
    }

    @Test
    public void test2a() throws Exception {
        RandomUtil.reseedThreadLocal(47);
        final InputTestData input = createTestData(47);
        final NucleotideSequence reference = input.reference;
        final EnumMap<GeneType, int[]> refPositions = input.refPositions;
        NucleotideSequence left1 = reference.getRange(refPositions.get(Diversity)[0] - 85, refPositions.get(Diversity)[0] + 10);
        final char[] chars = left1.toString().toCharArray();
        chars[3] = 'a';
        left1 = new NucleotideSequence(new String(chars));
        PairedRead[] data = {
                createPair(0, left1, reference.getRange(refPositions.get(Diversity)[1], refPositions.get(Diversity)[1] + 85).getReverseComplement()),
                createPair(1, reference.getRange(refPositions.get(Diversity)[0] - 135, refPositions.get(Diversity)[0] - 30), reference.getRange(refPositions.get(Diversity)[0] - 8, refPositions.get(Diversity)[0] + 85).getReverseComplement()),
        };

        final TestResult testResult = processData(data, input);
        for (VDJCAlignments al : testResult.assembled) {
            MiXCRTestUtils.printAlignment(al);
//            System.out.println(input.VJJunction);
//            System.out.println(al.getFeature(GeneFeature.VJJunction).getSequence());
//            Assert.assertTrue(input.VJJunction.toString().contains(al.getFeature(GeneFeature.VJJunction).getSequence().toString()));
        }
    }

    @Test
    public void test3() throws Exception {
        for (int i = 0; i < 100; i++) {
            RandomUtil.reseedThreadLocal(i);
//            System.out.println(i);
            final InputTestData input = createTestData(i);
            final NucleotideSequence reference = input.reference;
            final EnumMap<GeneType, int[]> refPositions = input.refPositions;
            PairedRead[] data = {
                    createPair(0, reference.getRange(refPositions.get(Diversity)[0] - 85, refPositions.get(Diversity)[0] + 10), reference.getRange(refPositions.get(Diversity)[1], refPositions.get(Diversity)[1] + 85).getReverseComplement()),
                    createPair(1, reference.getRange(refPositions.get(Diversity)[0] - 135, refPositions.get(Diversity)[0] - 70), reference.getRange(refPositions.get(Diversity)[0] - 8, refPositions.get(Diversity)[0] + 85).getReverseComplement())
            };

            final TestResult testResult = processData(data, input);
            for (VDJCAlignments al : testResult.assembled) {
//                printAlignment(al);
                if (al.numberOfTargets() == 1) {
//                    System.out.println(input.VJJunction);
//                    System.out.println(al.getFeature(GeneFeature.VJJunction).getSequence());
                    Assert.assertTrue(input.VJJunction.toString().contains(al.getFeature(GeneFeature.VJJunction).getSequence().toString()));
                }
            }
        }
    }

    static <V> EnumMap<GeneType, V> gtMap() {
        return new EnumMap<>(GeneType.class);
    }

    static class InputTestData {
        final EnumMap<GeneType, VDJCGene> genes;
        final EnumMap<GeneType, NucleotideSequence> germlineRegions;
        final EnumMap<GeneType, int[]> germlineCuts;
        final EnumMap<GeneType, int[]> refPositions;
        final NucleotideSequence VDJunction, DJJunction, reference, VJJunction;

        public InputTestData(EnumMap<GeneType, VDJCGene> genes, EnumMap<GeneType, NucleotideSequence> germlineRegions, EnumMap<GeneType, int[]> germlineCuts, EnumMap<GeneType, int[]> refPositions, NucleotideSequence VDJunction, NucleotideSequence DJJunction, NucleotideSequence reference, NucleotideSequence VJJunction) {
            this.genes = genes;
            this.germlineRegions = germlineRegions;
            this.germlineCuts = germlineCuts;
            this.refPositions = refPositions;
            this.VDJunction = VDJunction;
            this.DJJunction = DJJunction;
            this.reference = reference;
            this.VJJunction = VJJunction;
        }
    }

    static class TestResult {
        final PairedRead[] inputReads;
        final RunMiXCR.AlignResult inputAlignments;
        final ArrayList<VDJCAlignments> assembled;

        public TestResult(PairedRead[] inputReads, RunMiXCR.AlignResult inputAlignments, ArrayList<VDJCAlignments> assembled) {
            this.inputReads = inputReads;
            this.inputAlignments = inputAlignments;
            this.assembled = assembled;
        }
    }

    public static TestResult processData(PairedRead[] data, InputTestData input) throws Exception {

        RunMiXCR.RunMiXCRAnalysis params = new RunMiXCR.RunMiXCRAnalysis(data);
        params.alignerParameters.setAllowPartialAlignments(true);

        final RunMiXCR.AlignResult inputAlignments = RunMiXCR.align(params);
        //inputAlignments.report.writeReport(new ReportHelper(System.out));
        //System.out.println("\n");

        for (VDJCAlignments al : inputAlignments.alignments) {
            for (GeneType gt : GeneType.VJC_REFERENCE) {
                final VDJCHit[] hits = al.getHits(gt);
                if (hits == null)
                    continue;
                boolean yes = false;
                for (VDJCHit hit : hits) {
                    if (input.genes.get(gt).equals(hit.getGene())) {
                        yes = true;
                        break;
                    }
                }
//                Assert.assertTrue(yes);
            }

//            if (al.getFeature(GeneFeature.VJJunction) != null)
//                Assert.assertTrue(input.VJJunction.toString().contains(al.getFeature(GeneFeature.VJJunction).getSequence().toString()));
        }


        final ByteArrayOutputStream overlappedSerializedData = new ByteArrayOutputStream();
        try (VDJCAlignmentsWriter writer = new VDJCAlignmentsWriter(overlappedSerializedData)) {
            final PartialAlignmentsAssemblerParameters pParameters = PartialAlignmentsAssemblerParameters.getDefault();
            pParameters.setMergerParameters(pParameters.getMergerParameters().overrideMinimalIdentity(0.0));
            PartialAlignmentsAssembler assembler = new PartialAlignmentsAssembler(pParameters, writer, true, false);

            try (final VDJCAlignmentsReader reader = inputAlignments.resultReader()) {
                assembler.buildLeftPartsIndex(reader);
            }
            try (final VDJCAlignmentsReader reader = inputAlignments.resultReader()) {
                assembler.searchOverlaps(reader);
            }
            //assembler.writeReport(new ReportHelper(System.out));
            //System.out.println("\n");
        }


        VDJCAlignmentsReader readResult = new VDJCAlignmentsReader(new ByteArrayInputStream(overlappedSerializedData.toByteArray()));

        final ArrayList<VDJCAlignments> overlapped = new ArrayList<>();
        VDJCAlignments al;
        while ((al = readResult.take()) != null)
            overlapped.add(al);
        return new TestResult(data, inputAlignments, overlapped);
    }

    public static InputTestData createTestData() throws Exception {
        return createTestData(System.currentTimeMillis());
    }

    public static InputTestData createTestData(long seed) throws Exception {
        EnumMap<GeneType, String> geneNames = new EnumMap<GeneType, String>(GeneType.class) {{
            put(Variable, "TRBV20-1*00");
            put(Diversity, "TRBD2*00");
            put(Joining, "TRBJ2-6*00");
            put(Constant, "TRBC2*00");
        }};

        //config
        RandomGenerator rnd = RandomUtil.getThreadLocalRandom();
        rnd.setSeed(seed);

        final VDJCAlignerParameters defaultFeatures = VDJCParametersPresets.getByName("default");
        defaultFeatures.getVAlignerParameters().setGeneFeatureToAlign(VRegion);
        defaultFeatures.getDAlignerParameters().setGeneFeatureToAlign(DRegion);
        defaultFeatures.getJAlignerParameters().setGeneFeatureToAlign(JRegion);

        //used alleles
        EnumMap<GeneType, VDJCGene> genes = new EnumMap<>(GeneType.class);
        //germline parts of sequences
        EnumMap<GeneType, NucleotideSequence> germlineRegions = gtMap();
        //left, right cut of germline
        EnumMap<GeneType, int[]> germlineCuts = gtMap();
        //begin, end positions in assembled sequence
        EnumMap<GeneType, int[]> refPositions = gtMap();
        //single assembled sequence
        SequenceBuilder<NucleotideSequence> referenceBuilder = NucleotideSequence.ALPHABET.createBuilder();

        NucleotideSequence VDJunction = TestUtil.randomSequence(NucleotideSequence.ALPHABET, 3, 10);
        NucleotideSequence DJJunction = TestUtil.randomSequence(NucleotideSequence.ALPHABET, 3, 10);

        for (GeneType gt : GeneType.VDJC_REFERENCE) {
            VDJCGene gene = VDJCLibraryRegistry.getDefault().getLibrary("default", "hs").get(geneNames.get(gt));
            NucleotideSequence seq = gene.getFeature(defaultFeatures.getFeatureToAlign(gt));

            int[] cuts = null;
            switch (gt) {
                case Variable:
                    cuts = new int[]{0, rnd.nextInt(gene.getFeature(GermlineVCDR3Part).size() - 5)};
                    break;
                case Diversity:
                    cuts = new int[]{rnd.nextInt(seq.size() / 3), rnd.nextInt(seq.size() / 3)};
                    break;
                case Joining:
                    cuts = new int[]{rnd.nextInt(gene.getFeature(GermlineJCDR3Part).size() - 5), 0};
                    break;
                case Constant:
                    cuts = new int[]{0, rnd.nextInt(seq.size() / 2)};
                    break;
            }

            NucleotideSequence gSeq = seq.getRange(cuts[0], seq.size() - cuts[1]);
            int[] positions = new int[2];

            positions[0] = referenceBuilder.size();
            referenceBuilder.append(gSeq);
            positions[1] = referenceBuilder.size();

            if (gt == Variable)
                referenceBuilder.append(VDJunction);
            if (gt == Diversity)
                referenceBuilder.append(DJJunction);

            genes.put(gt, gene);
            germlineCuts.put(gt, cuts);
            germlineRegions.put(gt, gSeq);
            refPositions.put(gt, positions);
        }

        NucleotideSequence VJJunction = NucleotideSequence.ALPHABET.createBuilder()
                .append(VDJunction)
                .append(germlineRegions.get(Diversity))
                .append(DJJunction)
                .createAndDestroy();

        NucleotideSequence reference = referenceBuilder.createAndDestroy();
        return new InputTestData(genes, germlineRegions, germlineCuts, refPositions, VDJunction, DJJunction, reference, VJJunction);
    }
}