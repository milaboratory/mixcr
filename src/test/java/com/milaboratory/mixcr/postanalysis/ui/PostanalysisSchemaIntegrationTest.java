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
package com.milaboratory.mixcr.postanalysis.ui;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.milaboratory.mixcr.basictypes.ClnsReader;
import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.basictypes.VDJCObject;
import com.milaboratory.mixcr.basictypes.VDJCSProperties;
import com.milaboratory.mixcr.postanalysis.Dataset;
import com.milaboratory.mixcr.postanalysis.PostanalysisResult;
import com.milaboratory.mixcr.postanalysis.PostanalysisRunner;
import com.milaboratory.mixcr.postanalysis.WeightFunctions;
import com.milaboratory.mixcr.postanalysis.additive.AAProperties;
import com.milaboratory.mixcr.postanalysis.additive.AdditiveCharacteristics;
import com.milaboratory.mixcr.postanalysis.additive.KeyFunctions;
import com.milaboratory.mixcr.postanalysis.diversity.DiversityCharacteristic;
import com.milaboratory.mixcr.postanalysis.downsampling.ClonesDownsamplingPreprocessorFactory;
import com.milaboratory.mixcr.postanalysis.downsampling.DownsampleValueChooser;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapCharacteristic;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapGroup;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapIntegrationTest;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapUtil;
import com.milaboratory.mixcr.postanalysis.preproc.NoPreprocessing;
import com.milaboratory.mixcr.postanalysis.preproc.OverlapPreprocessorAdapter;
import com.milaboratory.mixcr.postanalysis.spectratype.SpectratypeCharacteristic;
import com.milaboratory.mixcr.postanalysis.spectratype.SpectratypeKey;
import com.milaboratory.mixcr.postanalysis.spectratype.SpectratypeKeyFunction;
import com.milaboratory.mixcr.tests.IntegrationTest;
import com.milaboratory.util.LambdaSemaphore;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCLibraryRegistry;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.milaboratory.mixcr.postanalysis.additive.AdditiveCharacteristics.weightedBiophysics;
import static com.milaboratory.mixcr.postanalysis.additive.AdditiveCharacteristics.weightedLengthOf;

@Category(IntegrationTest.class)
public class PostanalysisSchemaIntegrationTest {

    private static ClonotypeDataset getClones(String sample) {
        return new ClonotypeDataset(sample, PostanalysisSchemaIntegrationTest.class.getResource("/sequences/big/yf_sample_data/" + sample + ".contigs.clns").getFile(), VDJCLibraryRegistry.getDefault());
    }

    @Test
    public void test1() throws IOException {
        ClonotypeDataset r1 = getClones("Ig-2_S2");
        ClonotypeDataset r2 = getClones("Ig-3_S3");
        ClonotypeDataset r3 = getClones("Ig-4_S4");
        ClonotypeDataset r4 = getClones("Ig-5_S5");
        ClonotypeDataset r5 = getClones("Ig1_S1");
        ClonotypeDataset r6 = getClones("Ig2_S2");
        ClonotypeDataset r7 = getClones("Ig3_S3");
        ClonotypeDataset r8 = getClones("Ig4_S4");
        ClonotypeDataset r9 = getClones("Ig5_S5");

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
                Arrays.asList(new GroupSummary.Simple<>())
        ));
        groups.add(new CharacteristicGroup<>(
                "diversity",
                Arrays.asList(new DiversityCharacteristic<>("diversity", new WeightFunctions.Count(), new ClonesDownsamplingPreprocessorFactory(new DownsampleValueChooser.Auto(), true))),
                Arrays.asList(new GroupSummary.Simple<>())
        ));

        groups.add(new CharacteristicGroup<>(
                "vUsage",
                Arrays.asList(AdditiveCharacteristics.segmentUsage(GeneType.Variable)),
                Arrays.asList(new GroupSummary.Simple<>())
        ));
        groups.add(new CharacteristicGroup<>(
                "jUsage",
                Arrays.asList(AdditiveCharacteristics.segmentUsage(GeneType.Joining)),
                Arrays.asList(new GroupSummary.Simple<>())
        ));
        groups.add(new CharacteristicGroup<>(
                "vjUsage",
                Arrays.asList(AdditiveCharacteristics.vjSegmentUsage()),
                Arrays.asList(new GroupSummary.VJUsage<>())
        ));


        groups.add(new CharacteristicGroup<>(
                "isotypeUsage",
                Arrays.asList(AdditiveCharacteristics.isotypeUsage()),
                Arrays.asList(new GroupSummary.Simple<>())
        ));

        groups.add(new CharacteristicGroup<>(
                "cdr3Spectratype",
                Arrays.asList(new SpectratypeCharacteristic("cdr3Spectratype",
                        NoPreprocessing.factory(), 10,
                        new SpectratypeKeyFunction<>(new KeyFunctions.NTFeature(GeneFeature.CDR3), GeneFeature.CDR3, false))),
                Collections.singletonList(new GroupSummary.Simple<>())));

        groups.add(new CharacteristicGroup<>(
                "VSpectratype",
                Arrays.asList(AdditiveCharacteristics.VSpectratype()),
                Collections.singletonList(new GroupSummary.Simple<>())));

        PostanalysisSchema<Clone> individualPA = new PostanalysisSchema<>(false, groups);

        ObjectMapper OM = new ObjectMapper();
        String individualPAStr = OM.writeValueAsString(individualPA);
        System.out.println(individualPAStr);
        PostanalysisSchema<?> json = OM.readValue(individualPAStr, PostanalysisSchema.class);
        Assert.assertEquals(individualPA, json);

        PostanalysisRunner<Clone> runner = new PostanalysisRunner<>();

        runner.addCharacteristics(individualPA.getAllCharacterisitcs());
        Dataset[] input = {r1, r2, r3, r4, r5, r6, r7, r8, r9};

        PostanalysisResult result = runner.run(input);

        String resultStr = OM.writeValueAsString(result);
        Assert.assertEquals(result, OM.readValue(resultStr, PostanalysisResult.class));

        CharacteristicGroupResult<SpectratypeKey<String>> r = result.getTable(individualPA.getGroup("cdr3Spectratype"));
        Assert.assertTrue(r.cells.stream()
                .collect(Collectors.groupingBy(c -> c.datasetId, Collectors.mapping(c -> c.key.payload, Collectors.toSet())))
                .values().stream().allMatch(l -> l.size() <= 11));

        System.out.println(result.getTable(individualPA.getGroup("VSpectratype")));


        Object[][] vjUsages = result.getTable(individualPA.getGroup("vjUsage")).getOutputs().get(GroupSummary.key).rows();
        System.out.println(Arrays.deepToString(vjUsages));
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
            Dataset<OverlapGroup<Clone>> overlap = OverlapUtil.overlap(sampleNames, by, readers);

            ClonesDownsamplingPreprocessorFactory downsamplePreprocessor = new ClonesDownsamplingPreprocessorFactory(
                    new DownsampleValueChooser.Minimal(),
                    true);

            List<OverlapCharacteristic<Clone>> overlaps = new ArrayList<>();
            for (int i = 0; i < readers.size(); ++i) {
                for (int j = i + 1; j < readers.size(); ++j) {
                    overlaps.add(new OverlapCharacteristic<>("overlap_" + i + "_" + j,
                            new WeightFunctions.Count(),
                            new OverlapPreprocessorAdapter.Factory<>(downsamplePreprocessor),
                            i, j));
                }
            }

            List<CharacteristicGroup<?, OverlapGroup<Clone>>> groups = new ArrayList<>();

            groups.add(new CharacteristicGroup<>("cdr3Properties",
                    overlaps,
                    Arrays.asList(new OverlapSummary<>())
            ));

            PostanalysisSchema<OverlapGroup<Clone>> overlapPA = new PostanalysisSchema<>(true, groups);
            ObjectMapper OM = new ObjectMapper();
            String overlapPAStr = OM.writeValueAsString(overlapPA);
            System.out.println(overlapPAStr);
            PostanalysisSchema<?> json = OM.readValue(overlapPAStr, PostanalysisSchema.class);
            Assert.assertEquals(overlapPA, json);


            PostanalysisRunner<OverlapGroup<Clone>> pr = new PostanalysisRunner<>();
            pr.addCharacteristics(overlapPA.getAllCharacterisitcs());

            PostanalysisResult result = pr.run(overlap);
            String resultStr = OM.writeValueAsString(result);
            Assert.assertEquals(result, OM.readValue(resultStr, PostanalysisResult.class));

            CharacteristicGroupResult<?> table = result.getTable(groups.get(0));
            System.out.println(table.getOutputs());
        }
    }
}
