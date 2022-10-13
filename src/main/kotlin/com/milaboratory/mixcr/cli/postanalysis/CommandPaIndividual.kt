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
import com.milaboratory.mixcr.cli.ValidationException
import com.milaboratory.mixcr.postanalysis.PostanalysisRunner
import com.milaboratory.mixcr.postanalysis.ui.ClonotypeDataset
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisParametersIndividual
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisParametersPreset
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisSchema
import com.milaboratory.util.JsonOverrider
import com.milaboratory.util.SmartProgressReporter
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine.Command

@Command(
    description = ["Run postanalysis for CDR3 metrics, diversity, V/J/VJ-usage, CDR3/V-Spectratype metrics"]
)
class CommandPaIndividual : CommandPa() {
    private val parameters: PostanalysisParametersIndividual by lazy {
        val result = PostanalysisParametersPreset.getByNameIndividual("default")
        result.defaultDownsampling = defaultDownsampling
        result.defaultDropOutliers = dropOutliers
        result.defaultOnlyProductive = onlyProductive
        result.defaultWeightFunction = defaultWeightFunction
        when {
            overrides.isEmpty() -> result
            else -> JsonOverrider.override(result, PostanalysisParametersIndividual::class.java, overrides)
                ?: throw ValidationException("Failed to override some parameter: $overrides")
        }
    }

    override fun run(group: IsolationGroup, samples: List<String>): PaResultByGroup {
        val groups = parameters.getGroups(group.chains, tagsInfo)
        val schema = PostanalysisSchema(false, groups)
        val runner = PostanalysisRunner<Clone>()
        runner.addCharacteristics(schema.allCharacterisitcs)
        val datasets = samples.map { file ->
            ClonotypeDataset(getSampleId(file), file, VDJCLibraryRegistry.getDefault())
        }
        SmartProgressReporter.startProgressReport(runner)
        return PaResultByGroup(group, schema, runner.run(datasets))
    }
}
