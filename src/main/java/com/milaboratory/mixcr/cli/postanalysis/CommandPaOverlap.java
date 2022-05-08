package com.milaboratory.mixcr.cli.postanalysis;

import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.postanalysis.*;
import com.milaboratory.mixcr.postanalysis.overlap.*;
import com.milaboratory.mixcr.postanalysis.preproc.ElementPredicate;
import com.milaboratory.mixcr.postanalysis.preproc.OverlapPreprocessorAdapter;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroup;
import com.milaboratory.mixcr.postanalysis.ui.OverlapSummary;
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisSchema;
import com.milaboratory.util.LambdaSemaphore;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.Chains;
import io.repseq.core.GeneFeature;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.*;

@Command(name = "overlap",
        sortOptions = false,
        separator = " ",
        description = "Overlap analysis")
public class CommandPaOverlap extends CommandPa {
    public static String Overlap = "overlap";

    @Option(description = "Override downsampling for F2 umi|d[number]|f[number]",
            names = {"--f2-downsampling"})
    public String f2downsampling;

    public CommandPaOverlap() {}

    @Override
    @SuppressWarnings("unchecked")
    PaResultByGroup run(IsolationGroup group, List<String> samples) {
        SetPreprocessorFactory<Clone> downsampling = downsampling();

        Map<OverlapType, SetPreprocessorFactory<Clone>> downsamplingByType = new HashMap<>();
        downsamplingByType.put(OverlapType.D, downsampling);
        downsamplingByType.put(OverlapType.F2, f2downsampling == null
                ? downsampling
                : downsampling(f2downsampling));
        downsamplingByType.put(OverlapType.R_Intersection, downsampling);

        List<VDJCSProperties.VDJCSProperty<VDJCObject>> ordering = VDJCSProperties.orderingByAminoAcid(new GeneFeature[]{GeneFeature.CDR3});
        OverlapPostanalysisSettings overlapPA = new OverlapPostanalysisSettings(
                ordering,
                new WeightFunctions.Count(),
                downsamplingByType
        );

        PostanalysisSchema<OverlapGroup<Clone>> schema = overlapPA.getSchema(samples.size(), group.chains.chains);

        // Limits concurrency across all readers
        LambdaSemaphore concurrencyLimiter = new LambdaSemaphore(32);
        List<CloneReader> readers = samples
                .stream()
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
                samples.stream().map(CommandPa::getSampleId).collect(toList()),
                ordering,
                readers);

        PostanalysisRunner<OverlapGroup<Clone>> runner = new PostanalysisRunner<>();
        runner.addCharacteristics(schema.getAllCharacterisitcs());

        System.out.println("Running for " + group);
        SmartProgressReporter.startProgressReport(runner);
        PostanalysisResult result = runner.run(overlapDataset);

        return new PaResultByGroup(group, schema, result);
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

            @Override
            public int numberOfClones() {
                return inner.numberOfClones();
            }
        };
    }

    @SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
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

        //fixme only productive??
        public List<OverlapCharacteristic<Clone>> getCharacteristics(int i, int j, Chains chain) {
            return groupped.entrySet().stream().map(e -> new OverlapCharacteristic<>("overlap_" + i + "_" + j + " / " + e.getValue().stream().map(t -> t.name).collect(Collectors.joining(" / ")), weight,
                    new OverlapPreprocessorAdapter.Factory<>(e.getKey().filterFirst(new ElementPredicate.IncludeChains(chain))),
                    e.getValue().toArray(new OverlapType[0]),
                    i, j)).collect(toList());
        }

        public PostanalysisSchema<OverlapGroup<Clone>> getSchema(int nSamples, Chains chain) {
            List<OverlapCharacteristic<Clone>> overlaps = new ArrayList<>();
            for (int i = 0; i < nSamples; ++i)
                for (int j = i; j < nSamples; ++j) // j=i to include diagonal elements
                    overlaps.addAll(getCharacteristics(i, j, chain));

            return new PostanalysisSchema<>(Collections.singletonList(
                    new CharacteristicGroup<>(Overlap,
                            overlaps,
                            Arrays.asList(new OverlapSummary<>())
                    )));
        }
    }
}
