package com.milaboratory.mixcr.postanalysis.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.milaboratory.mixcr.basictypes.ClnsReader;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCObject;
import com.milaboratory.mixcr.basictypes.VDJCSProperties;
import com.milaboratory.mixcr.postanalysis.PostanalysisResult;
import com.milaboratory.mixcr.postanalysis.PostanalysisRunner;
import com.milaboratory.mixcr.postanalysis.WeightFunctions;
import com.milaboratory.mixcr.postanalysis.additive.AAProperties;
import com.milaboratory.mixcr.postanalysis.additive.AdditiveCharacteristics;
import com.milaboratory.mixcr.postanalysis.additive.KeyFunctions;
import com.milaboratory.mixcr.postanalysis.diversity.DiversityCharacteristic;
import com.milaboratory.mixcr.postanalysis.downsampling.ClonesDownsamplingPreprocessor;
import com.milaboratory.mixcr.postanalysis.downsampling.ClonesOverlapDownsamplingPreprocessor;
import com.milaboratory.mixcr.postanalysis.downsampling.DownsampleValueChooser;
import com.milaboratory.mixcr.postanalysis.overlap.*;
import com.milaboratory.mixcr.postanalysis.preproc.NoPreprocessing;
import com.milaboratory.mixcr.postanalysis.spectratype.SpectratypeCharacteristic;
import com.milaboratory.mixcr.postanalysis.spectratype.SpectratypeKey;
import com.milaboratory.mixcr.postanalysis.spectratype.SpectratypeKeyFunction;
import com.milaboratory.util.LambdaSemaphore;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCLibraryRegistry;
import org.junit.Assert;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.milaboratory.mixcr.postanalysis.additive.AdditiveCharacteristics.weightedBiophysics;
import static com.milaboratory.mixcr.postanalysis.additive.AdditiveCharacteristics.weightedLengthOf;

/**
 *
 */
public class PostanalysisSchemaIntegrationTest {

    private static ClonesIterable getClones(String sample) {
        return new ClonesIterable(PostanalysisSchemaIntegrationTest.class.getResource("/sequences/big/yf_sample_data/" + sample + ".contigs.clns").getFile(), VDJCLibraryRegistry.getDefault());
    }

    @Test
    public void test1() throws IOException {
        ClonesIterable r1 = getClones("Ig-2_S2");
        ClonesIterable r2 = getClones("Ig-3_S3");
        ClonesIterable r3 = getClones("Ig-4_S4");
        ClonesIterable r4 = getClones("Ig-5_S5");
        ClonesIterable r5 = getClones("Ig1_S1");
        ClonesIterable r6 = getClones("Ig2_S2");
        ClonesIterable r7 = getClones("Ig3_S3");
        ClonesIterable r8 = getClones("Ig4_S4");
        ClonesIterable r9 = getClones("Ig5_S5");

        List<CharacteristicGroup<?, Clone>> groups = new ArrayList<>();

        groups.add(new CharacteristicGroup<>(
                "cdr3Properties",
                Arrays.asList(
                        weightedLengthOf(GeneFeature.CDR3, false),
                        weightedLengthOf(GeneFeature.VJJunction, false),
                        weightedLengthOf(new GeneFeature(GeneFeature.VDJunction, GeneFeature.DJJunction), false).setName("ntLengthOfN"),
                        weightedBiophysics(AAProperties.AAProperty.N2Strength, GeneFeature.CDR3, AAProperties.Adjustment.LeadingCenter, 5),
                        weightedBiophysics(AAProperties.AAProperty.N2Hydrophobicity, GeneFeature.CDR3, AAProperties.Adjustment.LeadingCenter, 5),
                        weightedBiophysics(AAProperties.AAProperty.N2Surface, GeneFeature.CDR3, AAProperties.Adjustment.LeadingCenter, 5),
                        weightedBiophysics(AAProperties.AAProperty.N2Volume, GeneFeature.CDR3, AAProperties.Adjustment.LeadingCenter, 5),
                        weightedBiophysics(AAProperties.AAProperty.Charge, GeneFeature.CDR3, AAProperties.Adjustment.LeadingCenter, 5)
                ),
                Arrays.asList(new GroupSummary<>())
        ));
        groups.add(new CharacteristicGroup<>(
                "diversity",
                Arrays.asList(new DiversityCharacteristic<>("diversity", new WeightFunctions.Count(), new ClonesDownsamplingPreprocessor(new DownsampleValueChooser.Auto(), 314))),
                Arrays.asList(new GroupSummary<>())
        ));

        groups.add(new CharacteristicGroup<>(
                "vUsage",
                Arrays.asList(AdditiveCharacteristics.segmentUsage(GeneType.Variable)),
                Arrays.asList(new GroupSummary<>())
        ));
        groups.add(new CharacteristicGroup<>(
                "jUsage",
                Arrays.asList(AdditiveCharacteristics.segmentUsage(GeneType.Joining)),
                Arrays.asList(new GroupSummary<>())
        ));
        groups.add(new CharacteristicGroup<>(
                "vjUsage",
                Arrays.asList(AdditiveCharacteristics.vjSegmentUsage()),
                Arrays.asList(new GroupSummary<>(), new GroupMelt.VJUsageMelt<>())
        ));


        groups.add(new CharacteristicGroup<>(
                "isotypeUsage",
                Arrays.asList(AdditiveCharacteristics.isotypeUsage()),
                Arrays.asList(new GroupSummary<>())
        ));

        groups.add(new CharacteristicGroup<>(
                "cdr3Spectratype",
                Arrays.asList(new SpectratypeCharacteristic("cdr3Spectratype",
                        new NoPreprocessing<>(), 10,
                        new SpectratypeKeyFunction<>(new KeyFunctions.NTFeature(GeneFeature.CDR3), GeneFeature.CDR3, false))),
                Collections.singletonList(new GroupSummary<>())));

        groups.add(new CharacteristicGroup<>(
                "VSpectratype",
                Arrays.asList(AdditiveCharacteristics.VSpectratype()),
                Collections.singletonList(new GroupSummary<>())));

        PostanalysisSchema<Clone> individualPA = new PostanalysisSchema<>(groups);

        ObjectMapper OM = new ObjectMapper();
        String individualPAStr = OM.writeValueAsString(individualPA);
        System.out.println(individualPAStr);
        PostanalysisSchema<?> json = OM.readValue(individualPAStr, PostanalysisSchema.class);
        Assert.assertEquals(individualPA, json);

        PostanalysisRunner<Clone> runner = new PostanalysisRunner<>();

        runner.addCharacteristics(individualPA.getAllCharacterisitcs());
        Iterable[] input = {r1, r2, r3, r4, r5, r6, r7, r8, r9};
        runner.setDatasets(input);

        PostanalysisResult result = runner.run().setSampleIds(IntStream.range(0, 9).mapToObj(i -> "" + i).collect(Collectors.toList()));
        result.setSampleIds(IntStream.range(0, input.length).mapToObj(i -> "" + i).collect(Collectors.toList()));

        String resultStr = OM.writeValueAsString(result);
        Assert.assertEquals(result, OM.readValue(resultStr, PostanalysisResult.class));
        Map<String, OutputTable> outputs;

//        CharacteristicGroupResult<String> cdr3Table = result.getTable(cdr3PropsTable);
//        cdr3Table.getOutputs();
//        for (OutputTable o : outputs.values()) {
//            o.writeTSV(Paths.get("/Users/poslavskysv/Downloads/hhhh/"));
//        }
//        System.out.println(outputs);
//
//        CharacteristicGroupResult<DiversityMeasure> divTable = result.getTable(diversityTable);
//        divTable.cells.stream().filter(cell -> cell.key == DiversityMeasure.Observed).map(cell -> cell.sampleIndex + " - " + cell.value).forEach(System.out::println);
//        divTable.cells.stream().filter(cell -> cell.key == DiversityMeasure.Chao1).map(cell -> cell.sampleIndex + " - " + cell.value).forEach(System.out::println);
//        outputs = divTable.getOutputs();
//        for (OutputTable o : outputs.values()) {
//            o.writeTSV(Paths.get("/Users/poslavskysv/Downloads/hhhh"));
//        }

        CharacteristicGroupResult<SpectratypeKey<String>> r = result.getTable(individualPA.getGroup("cdr3Spectratype"));
        Assert.assertTrue(r.cells.stream()
                .collect(Collectors.groupingBy(c -> c.sampleIndex, Collectors.mapping(c -> c.key.payload, Collectors.toSet())))
                .values().stream().allMatch(l -> l.size() <= 11));

        System.out.println(result.getTable(individualPA.getGroup("VSpectratype")));
    }

    @Test
    public void testOverlap1() throws JsonProcessingException {
        // Limits concurrency across all readers
        LambdaSemaphore concurrencyLimiter = new LambdaSemaphore(4);

        List<String> sampleNames = Arrays.asList(
                "Ig-2_S2.contigs.clns",
                "Ig-3_S3.contigs.clns",
                "Ig-4_S4.contigs.clns",
                "Ig-2_S2.contigs.clns",
                "Ig1_S1.contigs.clns",
                "Ig2_S2.contigs.clns",
                "Ig3_S3.contigs.clns",
                "Ig4_S4.contigs.clns",
                "Ig5_S5.contigs.clns");
        List<ClnsReader> readers = sampleNames.stream()
                .map(f -> {
                    try {
                        return new ClnsReader(
                                Paths.get(OverlapIntegrationTest.class.getResource("/sequences/big/yf_sample_data/" + f).getFile()),
                                VDJCLibraryRegistry.getDefault(),
                                concurrencyLimiter);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.toList());


        List<VDJCSProperties.VDJCSProperty<VDJCObject>> byN = VDJCSProperties.orderingByNucleotide(new GeneFeature[]{GeneFeature.CDR3});
        List<VDJCSProperties.VDJCSProperty<VDJCObject>> byAA = VDJCSProperties.orderingByAminoAcid(new GeneFeature[]{GeneFeature.CDR3});
        List<VDJCSProperties.VDJCSProperty<VDJCObject>> byAAAndV = VDJCSProperties.orderingByAminoAcid(new GeneFeature[]{GeneFeature.CDR3}, GeneType.Variable);

        for (List<VDJCSProperties.VDJCSProperty<VDJCObject>> by : Arrays.asList(byN, byAA, byAAAndV)) {
            System.out.println("=============");
            OverlapIterable<Clone> overlap = OverlapUtil.overlap(by, readers);

            ClonesOverlapDownsamplingPreprocessor downsamplePreprocessor = new ClonesOverlapDownsamplingPreprocessor(
                    new DownsampleValueChooser.Minimal(),
                    332142);

            List<OverlapCharacteristic<Clone>> overlaps = new ArrayList<>();
            for (int i = 0; i < readers.size(); ++i) {
                for (int j = i + 1; j < readers.size(); ++j) {
                    overlaps.add(new OverlapCharacteristic<>("overlap_" + i + "_" + j,
                            new WeightFunctions.Count(),
                            downsamplePreprocessor,
                            i, j));
                }
            }


            List<CharacteristicGroup<?, OverlapGroup<Clone>>> groups = new ArrayList<>();

            groups.add(new CharacteristicGroup<>("cdr3Properties",
                    overlaps,
                    Arrays.asList(new OverlapSummary<>())
            ));

            PostanalysisSchema<OverlapGroup<Clone>> overlapPA = new PostanalysisSchema<>(groups);
            ObjectMapper OM = new ObjectMapper();
            String overlapPAStr = OM.writeValueAsString(overlapPA);
            System.out.println(overlapPAStr);
            PostanalysisSchema<?> json = OM.readValue(overlapPAStr, PostanalysisSchema.class);
            Assert.assertEquals(overlapPA, json);


            PostanalysisRunner<OverlapGroup<Clone>> pr = new PostanalysisRunner<>();
            pr.setDatasets(Arrays.asList(overlap));
            pr.addCharacteristics(overlapPA.getAllCharacterisitcs());

            PostanalysisResult result = pr.run().setSampleIds(sampleNames);
            String resultStr = OM.writeValueAsString(result);
            Assert.assertEquals(result, OM.readValue(resultStr, PostanalysisResult.class));

            CharacteristicGroupResult<?> table = result.getTable(groups.get(0));
            System.out.println(table.getOutputs());
        }
    }
}
