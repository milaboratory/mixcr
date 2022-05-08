package com.milaboratory.mixcr.cli.postanalysis;

import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.postanalysis.Dataset;
import com.milaboratory.mixcr.postanalysis.PostanalysisRunner;
import com.milaboratory.mixcr.postanalysis.SetPreprocessorFactory;
import com.milaboratory.mixcr.postanalysis.WeightFunctions;
import com.milaboratory.mixcr.postanalysis.additive.AAProperties;
import com.milaboratory.mixcr.postanalysis.additive.AdditiveCharacteristics;
import com.milaboratory.mixcr.postanalysis.additive.KeyFunctions;
import com.milaboratory.mixcr.postanalysis.diversity.DiversityCharacteristic;
import com.milaboratory.mixcr.postanalysis.diversity.DiversityMeasure;
import com.milaboratory.mixcr.postanalysis.preproc.ElementPredicate;
import com.milaboratory.mixcr.postanalysis.preproc.SelectTop;
import com.milaboratory.mixcr.postanalysis.spectratype.SpectratypeCharacteristic;
import com.milaboratory.mixcr.postanalysis.spectratype.SpectratypeKeyFunction;
import com.milaboratory.mixcr.postanalysis.ui.*;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine.Command;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.milaboratory.mixcr.postanalysis.additive.AdditiveCharacteristics.*;

@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
@Command(name = "individual",
        sortOptions = false,
        separator = " ",
        description = "Biophysics, Diversity, V/J/VJ-Usage, CDR3/V-Spectratype")
public class CommandPaIndividual extends CommandPa {
    static final String
            Biophysics = "biophysics",
            Diversity = "diversity",
            VUsage = "vUsage",
            JUsage = "JUsage",
            VJUsage = "VJUsage",
            IsotypeUsage = "IsotypeUsage",
            CDR3Spectratype = "CDR3Spectratype",
            VSpectratype = "VSpectratype",
            VSpectratypeMean = "VSpectratypeMean";

    public CommandPaIndividual() {}

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    PaResultByGroup run(IsolationGroup group, List<String> samples) {
        List<CharacteristicGroup<?, Clone>> groups = new ArrayList<>();

        SetPreprocessorFactory<Clone> downsampling = downsampling()
                .filterFirst(new ElementPredicate.IncludeChains(group.chains.chains));

        groups.add(new CharacteristicGroup<>(Biophysics,
                Arrays.asList(
                        weightedLengthOf(downsampling, GeneFeature.CDR3, false).setName("CDR3 length, nt"),
                        weightedLengthOf(downsampling, GeneFeature.CDR3, true).setName("CDR3 length, aa"),
                        weightedLengthOf(downsampling, GeneFeature.VJJunction, false).setName("NDN length, nt"),
                        weightedAddedNucleotides(downsampling).setName("Added N, nt"),
                        weightedBiophysics(downsampling, AAProperties.AAProperty.N2Strength, GeneFeature.CDR3, AAProperties.Adjustment.LeadingCenter, 5).setName("Strength"),
                        weightedBiophysics(downsampling, AAProperties.AAProperty.N2Hydrophobicity, GeneFeature.CDR3, AAProperties.Adjustment.LeadingCenter, 5).setName("Hydrophobicity"),
                        weightedBiophysics(downsampling, AAProperties.AAProperty.N2Surface, GeneFeature.CDR3, AAProperties.Adjustment.LeadingCenter, 5).setName("Surface"),
                        weightedBiophysics(downsampling, AAProperties.AAProperty.N2Volume, GeneFeature.CDR3, AAProperties.Adjustment.LeadingCenter, 5).setName("Volume"),
                        weightedBiophysics(downsampling, AAProperties.AAProperty.Charge, GeneFeature.CDR3, AAProperties.Adjustment.LeadingCenter, 5).setName("Charge")
                ),
                Arrays.asList(new GroupSummary<>())
        ));

        groups.add(new CharacteristicGroup<>(Diversity,
                Arrays.asList(new DiversityCharacteristic<>("Diversity", new WeightFunctions.Count(),
                                downsampling,
                                new DiversityMeasure[]{
                                        DiversityMeasure.Observed,
                                        DiversityMeasure.Clonality,
                                        DiversityMeasure.ShannonWeiner,
                                        DiversityMeasure.InverseSimpson,
                                        DiversityMeasure.Chao1,
                                        DiversityMeasure.Gini
                                }),
                        new DiversityCharacteristic<>("d50", new WeightFunctions.Count(),
                                downsampling.then(new SelectTop.Factory<>(WeightFunctions.Default(), 0.5)),
                                new DiversityMeasure[]{
                                        DiversityMeasure.Observed.overrideName("d50")
                                })),
                Arrays.asList(new GroupSummary<>())
        ));

        groups.add(new CharacteristicGroup<>(VUsage,
                Arrays.asList(AdditiveCharacteristics.segmentUsage(downsampling, GeneType.Variable)),
                Arrays.asList(new GroupSummary<>())
        ));
        groups.add(new CharacteristicGroup<>(JUsage,
                Arrays.asList(AdditiveCharacteristics.segmentUsage(downsampling, GeneType.Joining)),
                Arrays.asList(new GroupSummary<>())
        ));
        groups.add(new CharacteristicGroup<>(VJUsage,
                Arrays.asList(AdditiveCharacteristics.vjSegmentUsage(downsampling)),
                Arrays.asList(new GroupSummary<>(), new GroupMelt.VJUsageMelt<>())
        ));

        groups.add(new CharacteristicGroup<>(IsotypeUsage,
                Arrays.asList(AdditiveCharacteristics.isotypeUsage(downsampling)),
                Arrays.asList(new GroupSummary<>())
        ));

        groups.add(new CharacteristicGroup<>(CDR3Spectratype,
                Arrays.asList(new SpectratypeCharacteristic("CDR3 spectratype",
                        downsampling, 10,
                        new SpectratypeKeyFunction<>(new KeyFunctions.AAFeature(GeneFeature.CDR3), GeneFeature.CDR3, false))),
                Collections.singletonList(new GroupSummary<>())));

        groups.add(new CharacteristicGroup<>(VSpectratype,
                Arrays.asList(AdditiveCharacteristics.VSpectratype(downsampling)),
                Collections.singletonList(new GroupSummary<>())));

        groups.add(new CharacteristicGroup<>(VSpectratypeMean,
                Arrays.asList(AdditiveCharacteristics.VSpectratypeMean(downsampling)),
                Collections.singletonList(new GroupSummary<>())));

        PostanalysisSchema<Clone> schema = new PostanalysisSchema<>(groups);
        PostanalysisRunner runner = new PostanalysisRunner<>();
        runner.addCharacteristics(schema.getAllCharacterisitcs());

        List<Dataset> datasets = samples.stream()
                .map(file ->
                        new ClonotypeDataset(getSampleId(file), file, VDJCLibraryRegistry.getDefault())
                ).collect(Collectors.toList());

        System.out.println("Running for " + group);
        SmartProgressReporter.startProgressReport(runner);
        return new PaResultByGroup(group, schema, runner.run(datasets));
    }
}
