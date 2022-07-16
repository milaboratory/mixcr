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
import com.milaboratory.mixcr.basictypes.CloneReader;
import com.milaboratory.mixcr.basictypes.CloneReaderMerger;
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
import com.milaboratory.util.LambdaSemaphore;
import com.milaboratory.util.SmartProgressReporter;
import com.milaboratory.util.StringUtil;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Command(name = "overlap",
        sortOptions = false,
        separator = " ",
        description = "Overlap analysis")
public class CommandPaOverlap extends CommandPa {
    @Option(description = CommonDescriptions.OVERLAP_CRITERIA,
            names = {"--criteria"})
    public String overlapCriteria = "CDR3|AA|V|J";

    @Option(description = "Aggregate samples in groups by specified metadata columns",
            names = {"--factor-by"},
            split = ",")
    public List<String> factoryBy;

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

    private OverlapDataset<Clone> overlapDataset(IsolationGroup group, List<String> samples) {
        if (factoryBy == null || factoryBy.isEmpty())
            return OverlapUtil.overlap(
                    samples,
                    new ElementPredicate.IncludeChains(group.chains.chains),
                    OverlapUtil.parseCriteria(overlapCriteria).ordering()
            );
        else {
            Map<String, List<Object>> metadata = metadata();
            @SuppressWarnings("unchecked")
            List<String> mSamples = (List) metadata.get("sample");

            // sample -> metadata sample
            Map<String, String> sample2meta = StringUtil.matchLists(samples, mSamples);
            for (Map.Entry<String, String> e : sample2meta.entrySet()) {
                if (e.getValue() == null)
                    throw new IllegalArgumentException("Malformed metadata: can't find metadata row for sample " + e.getKey());
            }

            // metadata sample -> actual sample
            Map<String, String> meta2sample = sample2meta.entrySet().stream()
                    .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

            // agg group -> sample
            Map<String, List<String>> group2samples = new HashMap<>();
            for (int i = 0; i < mSamples.size(); i++) {
                int iSample = i;

                String sample = meta2sample.get(mSamples.get(i));
                if (sample == null)
                    continue;

                String aggGroup = factoryBy.stream()
                        .map(a -> metadata.get(a).get(iSample).toString())
                        .collect(Collectors.joining(","));

                group2samples.computeIfAbsent(aggGroup, __ -> new ArrayList<>())
                        .add(sample);
            }

            List<String> datasetIds = new ArrayList<>();
            List<CloneReader> readers = new ArrayList<>();
            // Limits concurrency across all readers
            LambdaSemaphore concurrencyLimiter = new LambdaSemaphore(32);
            for (Map.Entry<String, List<String>> e : group2samples.entrySet()) {
                CloneReaderMerger reader = new CloneReaderMerger(e.getValue().stream().map(it ->
                {
                    try {
                        return OverlapUtil.mkCheckedReader(Paths.get(it),
                                new ElementPredicate.IncludeChains(group.chains.chains),
                                concurrencyLimiter
                        );
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }).collect(Collectors.toList()));

                datasetIds.add(e.getKey());
                readers.add(reader);
            }
            return OverlapUtil.overlap(
                    datasetIds,
                    OverlapUtil.parseCriteria(overlapCriteria).ordering(),
                    readers
            );
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    PaResultByGroup run(IsolationGroup group, List<String> samples) {
        OverlapDataset<Clone> overlapDataset = overlapDataset(group, samples);
        List<CharacteristicGroup<?, OverlapGroup<Clone>>> groups = getParameters().getGroups(
                overlapDataset.datasetIds.size(),
                // we do not specify chains here, since we will filter
                // each dataset individually before overlap to speed up computations
                null,
                getTagsInfo());
        PostanalysisSchema<OverlapGroup<Clone>> schema = new PostanalysisSchema<>(true, groups);

        PostanalysisRunner<OverlapGroup<Clone>> runner = new PostanalysisRunner<>();
        runner.addCharacteristics(schema.getAllCharacterisitcs());

        SmartProgressReporter.startProgressReport(runner);
        PostanalysisResult result = runner.run(overlapDataset);

        return new PaResultByGroup(group, schema, result);
    }

}
