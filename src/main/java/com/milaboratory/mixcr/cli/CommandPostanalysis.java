package com.milaboratory.mixcr.cli;

import cc.redberry.pipe.OutputPortCloseable;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.postanalysis.*;
import com.milaboratory.mixcr.postanalysis.additive.AAProperties;
import com.milaboratory.mixcr.postanalysis.additive.AdditiveCharacteristics;
import com.milaboratory.mixcr.postanalysis.additive.KeyFunctions;
import com.milaboratory.mixcr.postanalysis.diversity.DiversityCharacteristic;
import com.milaboratory.mixcr.postanalysis.diversity.DiversityMeasure;
import com.milaboratory.mixcr.postanalysis.downsampling.ClonesDownsamplingPreprocessorFactory;
import com.milaboratory.mixcr.postanalysis.downsampling.DownsampleValueChooser;
import com.milaboratory.mixcr.postanalysis.overlap.*;
import com.milaboratory.mixcr.postanalysis.preproc.ElementPredicate;
import com.milaboratory.mixcr.postanalysis.preproc.OverlapPreprocessorAdapter;
import com.milaboratory.mixcr.postanalysis.preproc.SelectTop;
import com.milaboratory.mixcr.postanalysis.spectratype.SpectratypeCharacteristic;
import com.milaboratory.mixcr.postanalysis.spectratype.SpectratypeKeyFunction;
import com.milaboratory.mixcr.postanalysis.ui.*;
import com.milaboratory.util.GlobalObjectMappers;
import com.milaboratory.util.LambdaSemaphore;
import io.repseq.core.Chains;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.milaboratory.mixcr.postanalysis.additive.AdditiveCharacteristics.*;
import static java.util.stream.Collectors.*;

/**
 *
 */
public abstract class CommandPostanalysis extends ACommandWithOutputMiXCR {
    @Parameters(description = "cloneset.{clns|clna}... result.json")
    public List<String> inOut;

    @Option(description = "Use only productive sequences in postanalysis.",
            names = {"--only-productive"})
    public boolean onlyProductive = false;

    @Option(description = "Choose downsampling. Possible values: umi-count-[1000|auto]|cumulative-top-[percent]|top-[number]",
            names = {"-d", "--downsampling"},
            required = true)
    public String downsampling;

    @Option(description = "Chains",
            names = {"-c", "--chains"})
    public String chains = "ALL";

    @Option(description = "Prefix for outputs",
            names = {"-p", "--prefix"})
    public String prefix = "";

    List<String> inputs() {
        return inOut.subList(0, inOut.size() - 1)
                .stream()
                .flatMap(f -> {
                    if (Files.isDirectory(Path.of(f))) {
                        try {
                            return Files
                                    .list(Path.of(f))
                                    .map(Path::toString);
                        } catch (IOException ignored) {
                        }
                    }
                    return Stream.of(f);
                })
                .collect(toList());
    }

    String output() {
        return inOut.get(inOut.size() - 1);
    }

    String output(Chains.NamedChains chain) {
        return prefix + chain.name + "_" + output();
    }

    String outputBase() {
        return output().replace(".json", "");
    }

    static String baseName(String fName) {
        fName = Paths.get(fName).toAbsolutePath().getFileName().toString();
        int i = fName.lastIndexOf(".");
        if (i > 0)
            return fName.substring(0, i);
        else
            return fName;
    }

    static int downsamplingValue(String downsampling) {
        return Integer.parseInt(downsampling.substring(downsampling.lastIndexOf("-") + 1, downsampling.length()));
    }

    static SetPreprocessorFactory<Clone> parseDownsampling(String downsampling) {
        if (downsampling.startsWith("umi-count")) {
            if (downsampling.endsWith("auto"))
                return new ClonesDownsamplingPreprocessorFactory(new DownsampleValueChooser.Auto(), 314);
            else {
                return new ClonesDownsamplingPreprocessorFactory(new DownsampleValueChooser.Fixed(downsamplingValue(downsampling)), 314);
            }
        } else {
            int value = downsamplingValue(downsampling);
            if (downsampling.startsWith("cumulative-top")) {
                return new SelectTop.Factory<>(WeightFunctions.Count, 1.0 * value / 100.0);
            } else if (downsampling.startsWith("top")) {
                return new SelectTop.Factory<>(WeightFunctions.Count, value);
            } else {
                throw new IllegalArgumentException("Illegal downsampling string: " + downsampling);
            }
        }
    }

    SetPreprocessorFactory<Clone> downsampling() {
        return downsampling(this.downsampling);
    }

    SetPreprocessorFactory<Clone> downsampling(String downsamplingStr) {
        SetPreprocessorFactory<Clone> downsampling =
                parseDownsampling(downsamplingStr);

        if (onlyProductive) {
            List<ElementPredicate<Clone>> filters = new ArrayList<>();
            filters.add(new ElementPredicate.NoStops(GeneFeature.CDR3));
            filters.add(new ElementPredicate.NoOutOfFrames(GeneFeature.CDR3));
            downsampling = downsampling.filterFirst(filters);
        }

        return downsampling;
    }

    <K> void writeTables(Chains.NamedChains chain, CharacteristicGroupResult<K> tableResult) {
        for (CharacteristicGroupOutputExtractor<K> view : tableResult.group.views)
            for (OutputTable t : view.getTables(tableResult).values())
                t.writeTSV(Paths.get("").toAbsolutePath(), prefix + chain.name + "_");
    }

    ///////////////////////////////////////////// Individual /////////////////////////////////////////////


    @CommandLine.Command(name = "individual",
            sortOptions = false,
            separator = " ",
            description = "Biophysics, Diversity, V/J/VJ-Usage, CDR3/V-Spectratype")
    public static class CommandIndividual extends CommandPostanalysis {
        public CommandIndividual() {}

        @Override
        public void run0() throws Exception {
            Chains c = Chains.parse(chains);
            if (c.intersects(Chains.TRAD))
                run(Chains.TRAD_NAMED);
            if (c.intersects(Chains.TRG))
                run(Chains.TRG_NAMED);
            if (c.intersects(Chains.TRB))
                run(Chains.TRB_NAMED);
            if (c.intersects(Chains.IGH))
                run(Chains.IGH_NAMED);
            if (c.intersects(Chains.IGKL))
                run(Chains.IGKL_NAMED);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        void run(Chains.NamedChains chain) {
            List<CharacteristicGroup<?, Clone>> groups = new ArrayList<>();

            SetPreprocessorFactory<Clone> downsampling = downsampling()
                    .filterFirst(new ElementPredicate.IncludeChains(chain.chains));

            groups.add(new CharacteristicGroup<>(
                    "cdr3Properties",
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

            groups.add(new CharacteristicGroup<>(
                    "diversity",
                    Arrays.asList(new DiversityCharacteristic<>("diversity", new WeightFunctions.Count(),
                            downsampling,
                            new DiversityMeasure[]{
                                    DiversityMeasure.Observed,
                                    DiversityMeasure.Clonality,
                                    DiversityMeasure.ShannonWeiner,
                                    DiversityMeasure.InverseSimpson,
                                    DiversityMeasure.Chao1,
                                    DiversityMeasure.Gini
                            })),
                    Arrays.asList(new GroupSummary<>())
            ));

            groups.add(new CharacteristicGroup<>(
                    "vUsage",
                    Arrays.asList(AdditiveCharacteristics.segmentUsage(downsampling, GeneType.Variable)),
                    Arrays.asList(new GroupSummary<>())
            ));
            groups.add(new CharacteristicGroup<>(
                    "jUsage",
                    Arrays.asList(AdditiveCharacteristics.segmentUsage(downsampling, GeneType.Joining)),
                    Arrays.asList(new GroupSummary<>())
            ));
            groups.add(new CharacteristicGroup<>(
                    "vjUsage",
                    Arrays.asList(AdditiveCharacteristics.vjSegmentUsage(downsampling)),
                    Arrays.asList(new GroupSummary<>(), new GroupMelt.VJUsageMelt<>())
            ));

            groups.add(new CharacteristicGroup<>(
                    "isotypeUsage",
                    Arrays.asList(AdditiveCharacteristics.isotypeUsage(downsampling)),
                    Arrays.asList(new GroupSummary<>())
            ));

            groups.add(new CharacteristicGroup<>(
                    "cdr3Spectratype",
                    Arrays.asList(new SpectratypeCharacteristic("cdr3Spectratype",
                            downsampling, 10,
                            new SpectratypeKeyFunction<>(new KeyFunctions.AAFeature(GeneFeature.CDR3), GeneFeature.CDR3, false))),
                    Collections.singletonList(new GroupSummary<>())));

            groups.add(new CharacteristicGroup<>(
                    "VSpectratype",
                    Arrays.asList(AdditiveCharacteristics.VSpectratype(downsampling)),
                    Collections.singletonList(new GroupSummary<>())));

            groups.add(new CharacteristicGroup<>(
                    "VSpectratypeMean",
                    Arrays.asList(AdditiveCharacteristics.VSpectratypeMean(downsampling)),
                    Collections.singletonList(new GroupSummary<>())));

            PostanalysisSchema<Clone> schema = new PostanalysisSchema<>(groups);

            PostanalysisRunner runner = new PostanalysisRunner<>();
            runner.addCharacteristics(schema.getAllCharacterisitcs());

            List<Dataset> datasets = inputs().stream()
                    .map(file ->
                            new ClonotypeDataset(baseName(file), file, VDJCLibraryRegistry.getDefault())
                    ).collect(Collectors.toList());

            PostanalysisResult result = runner.run(datasets);
            for (CharacteristicGroup<?, Clone> table : schema.tables)
                writeTables(chain, result.getTable(table));

            try {
                GlobalObjectMappers.PRETTY.writeValue(new File(output(chain)), new PostanalysisData(schema, result));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    ///////////////////////////////////////////// Overlap /////////////////////////////////////////////

    @CommandLine.Command(name = "overlap",
            sortOptions = false,
            separator = " ",
            description = "Overlap analysis")
    public static class CommandOverlap extends CommandPostanalysis {
        @Option(description = "Override downsampling for F2 umi|d[number]|f[number]",
                names = {"--f2-downsampling"},
                required = false)
        public String f2downsampling;

        public CommandOverlap() {}

        @Override
        public void run0() throws Exception {
            Chains c = Chains.parse(chains);
            if (c.intersects(Chains.TRAD))
                run(Chains.TRAD_NAMED);
            if (c.intersects(Chains.TRG))
                run(Chains.TRG_NAMED);
            if (c.intersects(Chains.TRB))
                run(Chains.TRB_NAMED);
            if (c.intersects(Chains.IGH))
                run(Chains.IGH_NAMED);
            if (c.intersects(Chains.IGKL))
                run(Chains.IGKL_NAMED);
        }

        void run(Chains.NamedChains chain) {

            SetPreprocessorFactory<Clone> downsampling = downsampling();

            Map<OverlapType, SetPreprocessorFactory<Clone>> map = new HashMap<>();
            map.put(OverlapType.D, downsampling);
            map.put(OverlapType.F2, f2downsampling == null
                    ? downsampling
                    : downsampling(f2downsampling));
            map.put(OverlapType.R_Intersection, downsampling);

            List<VDJCSProperties.VDJCSProperty<VDJCObject>> ordering = VDJCSProperties.orderingByAminoAcid(new GeneFeature[]{GeneFeature.CDR3});
            OverlapPostanalysisSettings overlapPA = new OverlapPostanalysisSettings(
                    ordering,
                    new WeightFunctions.Count(),
                    map
            );

            PostanalysisSchema<OverlapGroup<Clone>> schema = overlapPA.getSchema(inputs().size(), chain.chains);

            // Limits concurrency across all readers
            LambdaSemaphore concurrencyLimiter = new LambdaSemaphore(32);
            List<CloneReader> readers = inputs().stream()
                    .map(s -> {
                        try {
                            return mkCheckedReader(
                                    Paths.get(s).toAbsolutePath(),
                                    concurrencyLimiter);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .collect(Collectors.toList());

            OverlapDataset<Clone> overlapDataset = OverlapUtil.overlap(
                    inputs().stream().map(CommandPostanalysis::baseName)
                            .collect(toList()),
                    ordering, readers);

            PostanalysisRunner<OverlapGroup<Clone>> runner = new PostanalysisRunner<>();
            runner.addCharacteristics(schema.getAllCharacterisitcs());
            PostanalysisResult result = runner.run(overlapDataset);

            for (CharacteristicGroup<?, OverlapGroup<Clone>> table : schema.tables)
                writeTables(chain, result.getTable(table));

            try {
                GlobalObjectMappers.PRETTY.writeValue(new File(output(chain)), new PostanalysisData(schema, result));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        public static CloneReader mkCheckedReader(Path path,
                                                  LambdaSemaphore concurrencyLimiter) throws IOException {
            ClnsReader inner = new ClnsReader(
                    path,
                    VDJCLibraryRegistry.getDefault(),
                    concurrencyLimiter);
            return new CloneReader() {
                @Override
                public VDJCSProperties.CloneOrdering ordering() {
                    return inner.ordering();
                }

                @Override
                public OutputPortCloseable<Clone> readClones() {
                    OutputPortCloseable<Clone> in = inner.readClones();
                    return new OutputPortCloseable<Clone>() {
                        @Override
                        public void close() {
                            in.close();
                        }

                        @Override
                        public Clone take() {
                            Clone t = in.take();
                            if (t == null)
                                return null;
                            if (t.getFeature(GeneFeature.CDR3) == null)
                                return take();
                            return t;
                        }
                    };
                }

                @Override
                public void close() throws Exception {
                    inner.close();
                }
            };
        }

        static final class OverlapPostanalysisSettings {
            final List<VDJCSProperties.VDJCSProperty<VDJCObject>> ordering;
            final WeightFunction<Clone> weight;
            final Map<OverlapType, SetPreprocessorFactory<Clone>> preprocessors;
            final Map<SetPreprocessorFactory<Clone>, List<OverlapType>> groupped;

            OverlapPostanalysisSettings(List<VDJCSProperties.VDJCSProperty<VDJCObject>> ordering,
                                        WeightFunction<Clone> weight,
                                        Map<OverlapType, SetPreprocessorFactory<Clone>> preprocessors) {
                this.ordering = ordering;
                this.weight = weight;
                this.preprocessors = preprocessors;
                this.groupped = preprocessors.entrySet().stream().collect(groupingBy(Map.Entry::getValue, mapping(Map.Entry::getKey, toList())));
            }

            private SetPreprocessorFactory<OverlapGroup<Clone>> getPreprocessor(OverlapType type, Chains chain) {
                return new OverlapPreprocessorAdapter.Factory<>(preprocessors.get(type).filterFirst(new ElementPredicate.IncludeChains(chain)));
            }

            public List<OverlapCharacteristic<Clone>> getCharacteristics(int i, int j, Chains chain) {
                return groupped.entrySet().stream().map(e -> new OverlapCharacteristic<>("overlap_" + i + "_" + j + e.getValue().stream().map(t -> t.name).collect(Collectors.joining("_")), weight,
                        new OverlapPreprocessorAdapter.Factory<>(e.getKey().filterFirst(new ElementPredicate.IncludeChains(chain))),
                        e.getValue().toArray(new OverlapType[0]),
                        i, j)).collect(toList());
            }

            public PostanalysisSchema<OverlapGroup<Clone>> getSchema(int nSamples, Chains chain) {
                List<OverlapCharacteristic<Clone>> overlaps = new ArrayList<>();
                for (int i = 0; i < nSamples; ++i)
                    for (int j = i + 1; j < nSamples; ++j)
                        overlaps.addAll(getCharacteristics(i, j, chain));

                return new PostanalysisSchema<>(Collections.singletonList(
                        new CharacteristicGroup<>("overlap",
                                overlaps,
                                Arrays.asList(new OverlapSummary<>())
                        )));
            }
        }
    }

    @CommandLine.Command(name = "postanalysis",
            separator = " ",
            description = "Run postanalysis routines.",
            subcommands = {
                    CommandLine.HelpCommand.class
            })
    public static class CommandPostanalysisMain {
    }

    public static final class PostanalysisData {
        @JsonProperty("schema")
        public final PostanalysisSchema<?> schema;
        @JsonProperty("result")
        public final PostanalysisResult result;

        @JsonCreator
        public PostanalysisData(@JsonProperty("schema") PostanalysisSchema<?> schema,
                                @JsonProperty("result") PostanalysisResult result) {
            this.schema = schema;
            this.result = result;
        }
    }
}
