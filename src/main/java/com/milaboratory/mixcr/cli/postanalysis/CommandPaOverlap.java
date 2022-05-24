package com.milaboratory.mixcr.cli.postanalysis;

import cc.redberry.pipe.OutputPortCloseable;
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters;
import com.milaboratory.mixcr.basictypes.*;
import com.milaboratory.mixcr.postanalysis.PostanalysisResult;
import com.milaboratory.mixcr.postanalysis.PostanalysisRunner;
import com.milaboratory.mixcr.postanalysis.WeightFunctions;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapDataset;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapGroup;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapUtil;
import com.milaboratory.mixcr.postanalysis.preproc.ElementPredicate;
import com.milaboratory.mixcr.postanalysis.preproc.FilterPreprocessor;
import com.milaboratory.mixcr.postanalysis.preproc.OverlapPreprocessorAdapter;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroup;
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisParametersOverlap;
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisParametersPreset;
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisSchema;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.util.JsonOverrider;
import com.milaboratory.util.LambdaSemaphore;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.GeneFeature;
import io.repseq.core.GeneType;
import io.repseq.core.VDJCGene;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

@Command(name = "overlap",
        sortOptions = false,
        separator = " ",
        description = "Overlap analysis")
public class CommandPaOverlap extends CommandPa {
    @Option(description = "Overlap criteria. Default CDR|AA|V|J",
            names = {"--criteria"})
    public String overlapCriteria = "CDR3|AA|V|J";

    public CommandPaOverlap() {}

    private PostanalysisParametersOverlap _parameters;

    private PostanalysisParametersOverlap getParameters() {
        if (_parameters != null)
            return _parameters;
        _parameters = PostanalysisParametersPreset.getByNameOverlap("default");
        _parameters.defaultDownsampling = defaultDownsampling;
        _parameters.defaultDropOutliers = dropOutliers;
        _parameters.defaultOnlyProductive = onlyProductive;
        if (!overrides.isEmpty()) {
            for (Map.Entry<String, String> o : overrides.entrySet())
                _parameters = JsonOverrider.override(_parameters, PostanalysisParametersOverlap.class, overrides);
            if (_parameters == null)
                throwValidationException("Failed to override some parameter: " + overrides);
        }
        return _parameters;
    }

    private List<VDJCSProperties.VDJCSProperty<VDJCObject>> parseCriteria() {
        String[] parts = overlapCriteria.toLowerCase().split("\\|");
        if (parts.length < 2)
            throwValidationException("Illegal criteria input: " + overlapCriteria);
        GeneFeature feature = GeneFeature.parse(parts[0]);
        if (!parts[1].equals("aa") && !parts[1].equals("nt"))
            throwValidationException("Illegal criteria input: " + overlapCriteria);
        boolean isAA = parts[1].equals("aa");
        List<GeneType> geneTypes = new ArrayList<>();
        if (parts.length > 2)
            if (!parts[2].equals("v"))
                throwValidationException("Illegal criteria input: " + overlapCriteria);
            else
                geneTypes.add(GeneType.Variable);
        if (parts.length > 3)
            if (!parts[3].equals("j"))
                throwValidationException("Illegal criteria input: " + overlapCriteria);
            else
                geneTypes.add(GeneType.Joining);

        if (isAA)
            return VDJCSProperties.orderingByAminoAcid(new GeneFeature[]{feature}, geneTypes.toArray(new GeneType[0]));
        else
            return VDJCSProperties.orderingByNucleotide(new GeneFeature[]{feature}, geneTypes.toArray(new GeneType[0]));
    }

    @Override
    @SuppressWarnings("unchecked")
    PaResultByGroup run(IsolationGroup group, List<String> samples) {
        List<CharacteristicGroup<?, OverlapGroup<Clone>>> groups = getParameters().getGroups(samples.size());
        PostanalysisSchema<OverlapGroup<Clone>> schema = new PostanalysisSchema<>(groups)
                .transform(ch -> ch.override(ch.name,
                        ch.preprocessor
                                .before(new OverlapPreprocessorAdapter.Factory<>(new FilterPreprocessor.Factory<>(WeightFunctions.Count, new ElementPredicate.IncludeChains(group.chains.chains)))))
                );

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
                parseCriteria(),
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

            @Override
            public List<VDJCGene> getUsedGenes() {
                return inner.getUsedGenes();
            }

            @Override
            public VDJCAlignerParameters getAlignerParameters() {
                return inner.getAlignerParameters();
            }

            @Override
            public CloneAssemblerParameters getAssemblerParameters() {
                return inner.getAssemblerParameters();
            }
        };
    }
}
