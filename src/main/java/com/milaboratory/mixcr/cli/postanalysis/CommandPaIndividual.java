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
import com.milaboratory.mixcr.postanalysis.Dataset;
import com.milaboratory.mixcr.postanalysis.PostanalysisRunner;
import com.milaboratory.mixcr.postanalysis.preproc.ElementPredicate;
import com.milaboratory.mixcr.postanalysis.ui.*;
import com.milaboratory.util.JsonOverrider;
import com.milaboratory.util.SmartProgressReporter;
import io.repseq.core.VDJCLibraryRegistry;
import picocli.CommandLine.Command;

import java.util.List;
import java.util.stream.Collectors;

@Command(name = "individual",
        sortOptions = false,
        separator = " ",
        description = "Run postanalysis for biophysics, diversity, V/J/VJ-usage, CDR3/V-Spectratype metrics")
public class CommandPaIndividual extends CommandPa {
    public CommandPaIndividual() {}

    private PostanalysisParametersIndividual _parameters;

    private PostanalysisParametersIndividual getParameters() {
        if (_parameters != null)
            return _parameters;
        _parameters = PostanalysisParametersPreset.getByNameIndividual("default");
        _parameters.defaultDownsampling = defaultDownsampling;
        _parameters.defaultDropOutliers = dropOutliers;
        _parameters.defaultOnlyProductive = onlyProductive;
        _parameters.defaultWeightFunction = defaultWeightFunction;
        if (!overrides.isEmpty()) {
            _parameters = JsonOverrider.override(_parameters, PostanalysisParametersIndividual.class, overrides);
            if (_parameters == null)
                throwValidationException("Failed to override some parameter: " + overrides);
        }
        return _parameters;
    }

    @Override
    @SuppressWarnings({"unchecked", "rawtypes"})
    PaResultByGroup run(IsolationGroup group, List<String> samples) {
        List<CharacteristicGroup<?, Clone>> groups = getParameters().getGroups(getTagsInfo());
        PostanalysisSchema<Clone> schema = new PostanalysisSchema<>(false, groups)
                .transform(ch -> ch.override(ch.name,
                        ch.preprocessor
                                .filterFirst(new ElementPredicate.IncludeChains(group.chains.chains)))
                );
        PostanalysisRunner runner = new PostanalysisRunner<>();
        runner.addCharacteristics(schema.getAllCharacterisitcs());

        List<Dataset> datasets = samples.stream()
                .map(file ->
                        new ClonotypeDataset(getSampleId(file), file, VDJCLibraryRegistry.getDefault())
                ).collect(Collectors.toList());

        SmartProgressReporter.startProgressReport(runner);
        return new PaResultByGroup(group, schema, runner.run(datasets));
    }
}
