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
package com.milaboratory.mixcr.cli.postanalysis;

import com.milaboratory.mixcr.basictypes.Clone;
import com.milaboratory.mixcr.cli.CommonDescriptions;
import com.milaboratory.mixcr.postanalysis.PostanalysisResult;
import com.milaboratory.mixcr.postanalysis.PostanalysisRunner;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapDataset;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapGroup;
import com.milaboratory.mixcr.postanalysis.overlap.OverlapUtil;
import com.milaboratory.mixcr.postanalysis.preproc.ElementPredicate;
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroup;
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisParametersOverlap;
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisParametersPreset;
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisSchema;
import com.milaboratory.util.JsonOverrider;
import com.milaboratory.util.SmartProgressReporter;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;

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
        _parameters.defaultWeightFunction = defaultWeightFunction;
        if (!overrides.isEmpty()) {
            _parameters = JsonOverrider.override(_parameters, PostanalysisParametersOverlap.class, overrides);
            if (_parameters == null)
                throwValidationException("Failed to override some parameter: " + overrides);
        }
        return _parameters;
    }

    @Override
    @SuppressWarnings("unchecked")
    PaResultByGroup run(IsolationGroup group, List<String> samples) {
        List<CharacteristicGroup<?, OverlapGroup<Clone>>> groups = getParameters().getGroups(samples.size(), group.chains.chains, getTagsInfo());
        PostanalysisSchema<OverlapGroup<Clone>> schema = new PostanalysisSchema<>(true, groups);

        OverlapDataset<Clone> overlapDataset = OverlapUtil.overlap(
                samples,
                new ElementPredicate.IncludeChains(group.chains.chains),
                OverlapUtil.parseCriteria(overlapCriteria).ordering()
        );

        PostanalysisRunner<OverlapGroup<Clone>> runner = new PostanalysisRunner<>();
        runner.addCharacteristics(schema.getAllCharacterisitcs());

        SmartProgressReporter.startProgressReport(runner);
        PostanalysisResult result = runner.run(overlapDataset);

        return new PaResultByGroup(group, schema, result);
    }

}
