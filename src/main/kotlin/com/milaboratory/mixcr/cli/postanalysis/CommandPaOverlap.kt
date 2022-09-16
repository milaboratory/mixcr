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
package com.milaboratory.mixcr.cli.postanalysis

import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.CloneReader
import com.milaboratory.mixcr.basictypes.CloneReaderMerger
import com.milaboratory.mixcr.cli.CommonDescriptions
import com.milaboratory.mixcr.postanalysis.PostanalysisRunner
import com.milaboratory.mixcr.postanalysis.overlap.OverlapDataset
import com.milaboratory.mixcr.postanalysis.overlap.OverlapGroup
import com.milaboratory.mixcr.postanalysis.overlap.OverlapUtil
import com.milaboratory.mixcr.postanalysis.preproc.ElementPredicate.IncludeChains
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisParametersOverlap
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisParametersPreset
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisSchema
import com.milaboratory.util.JsonOverrider
import com.milaboratory.util.LambdaSemaphore
import com.milaboratory.util.SmartProgressReporter
import com.milaboratory.util.StringUtil
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.nio.file.Paths

@Command(name = "overlap", sortOptions = false, separator = " ", description = ["Overlap analysis"])
class CommandPaOverlap : CommandPa() {
    @Option(description = [CommonDescriptions.OVERLAP_CRITERIA], names = ["--criteria"])
    var overlapCriteria = "CDR3|AA|V|J"

    @Option(
        description = ["Aggregate samples in groups by specified metadata columns"],
        names = ["--factor-by"],
        split = ","
    )
    var factoryBy: List<String> = mutableListOf()

    private val parameters: PostanalysisParametersOverlap by lazy {
        val result = PostanalysisParametersPreset.getByNameOverlap("default")
        result.defaultDownsampling = defaultDownsampling
        result.defaultDropOutliers = dropOutliers
        result.defaultOnlyProductive = onlyProductive
        result.defaultWeightFunction = defaultWeightFunction
        when {
            overrides.isEmpty() -> result
            else -> JsonOverrider.override(result, PostanalysisParametersOverlap::class.java, overrides)
                ?: throwValidationExceptionKotlin("Failed to override some parameter: $overrides")
        }
    }

    private fun overlapDataset(group: IsolationGroup, samples: List<String>): OverlapDataset<Clone> =
        if (factoryBy.isEmpty()) {
            OverlapUtil.overlap(
                samples,
                IncludeChains(group.chains, false),
                OverlapUtil.parseCriteria(overlapCriteria).ordering()
            )
        } else {
            val metadata = metadata!!
            val mSamples: List<String> = metadata["sample"]!!.map { it as String }

            // sample -> metadata sample
            val sample2meta = StringUtil.matchLists(samples, mSamples)
            for ((key, value) in sample2meta) {
                requireNotNull(value) { "Malformed metadata: can't find metadata row for sample $key" }
            }

            // metadata sample -> actual sample
            val meta2sample = sample2meta.entries.associate { (key, value) -> value to key }

            // agg group -> sample
            val group2samples = mutableMapOf<String, MutableList<String>>()
            for (i in mSamples.indices) {
                val sample = meta2sample[mSamples[i]] ?: continue
                val aggGroup = factoryBy.joinToString(",") { metadata[it]!![i].toString() }
                group2samples.computeIfAbsent(aggGroup) { mutableListOf() }
                    .add(sample)
            }
            val datasetIds = mutableListOf<String>()
            val readers = mutableListOf<CloneReader>()
            // Limits concurrency across all readers
            val concurrencyLimiter = LambdaSemaphore(32)
            for ((key, value) in group2samples) {
                val reader = CloneReaderMerger(value.map {
                    OverlapUtil.mkCheckedReader(
                        Paths.get(it),
                        IncludeChains(group.chains, false),
                        concurrencyLimiter
                    )
                })
                datasetIds += key
                readers += reader
            }
            OverlapUtil.overlap(
                datasetIds,
                OverlapUtil.parseCriteria(overlapCriteria).ordering(),
                readers
            )
        }

    override fun run(group: IsolationGroup, samples: List<String>): PaResultByGroup {
        val overlapDataset = overlapDataset(group, samples)
        val groups = parameters.getGroups(
            overlapDataset.datasetIds.size,  // we do not specify chains here, since we will filter
            // each dataset individually before overlap to speed up computations
            null,
            tagsInfo
        )
        val schema = PostanalysisSchema(true, groups)
        val runner = PostanalysisRunner<OverlapGroup<Clone>>()
        runner.addCharacteristics(schema.allCharacterisitcs)
        SmartProgressReporter.startProgressReport(runner)
        val result = runner.run(overlapDataset)
        return PaResultByGroup(group, schema, result)
    }
}
