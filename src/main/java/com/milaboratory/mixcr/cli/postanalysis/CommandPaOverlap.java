package com.milaboratory.mixcr.cli.postanalysis;

import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.cli.CommonDescriptions;
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
import com.milaboratory.util.JsonOverrider;
import com.milaboratory.util.SmartProgressReporter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.Map;

@Command(name = "overlap",
        sortOptions = false,
        separator = " ",
        description = "Overlap analysis")
public class CommandPaOverlap extends CommandPa {
    @Option(description = CommonDescriptions.OVERLAP_CRITERIA,
            names = {"--criteria"})
    public String overlapCriteria = "CDR3|AA|V|J";

    public CommandPaOverlap() {
    }

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

    @Override
    @SuppressWarnings("unchecked")
    PaResultByGroup run(IsolationGroup group, List<String> samples) {
        List<CharacteristicGroup<?, OverlapGroup<Clone>>> groups = getParameters().getGroups(samples.size());
        PostanalysisSchema<OverlapGroup<Clone>> schema = new PostanalysisSchema<>(groups)
                .transform(ch -> ch.override(ch.name,
                        ch.preprocessor
                                .before(new OverlapPreprocessorAdapter.Factory<>(new FilterPreprocessor.Factory<>(WeightFunctions.Count, new ElementPredicate.IncludeChains(group.chains.chains)))))
                );

        OverlapDataset<Clone> overlapDataset = OverlapUtil.overlap(
                samples,
                OverlapUtil.parseCriteria(overlapCriteria).ordering()
        );

        PostanalysisRunner<OverlapGroup<Clone>> runner = new PostanalysisRunner<>();
        runner.addCharacteristics(schema.getAllCharacterisitcs());

        System.out.println("Running for " + group);
        SmartProgressReporter.startProgressReport(runner);
        PostanalysisResult result = runner.run(overlapDataset);

        return new PaResultByGroup(group, schema, result);
    }
}
