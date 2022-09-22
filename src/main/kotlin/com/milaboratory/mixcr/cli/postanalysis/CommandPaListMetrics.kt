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

import com.fasterxml.jackson.module.kotlin.readValue
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.cli.AbstractMiXCRCommand
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroup
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisParametersIndividual.CDR3Metrics
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisParametersIndividual.Diversity
import com.milaboratory.util.GlobalObjectMappers
import picocli.CommandLine
import java.io.File
import java.io.IOException
import java.nio.file.Paths

@CommandLine.Command(
    name = "listMetrics",
    sortOptions = false,
    separator = " ",
    description = ["List available metrics"]
)
class CommandPaListMetrics : AbstractMiXCRCommand() {
    @CommandLine.Parameters(description = ["Input file with PA results"], index = "0")
    lateinit var `in`: String

    override fun getInputFiles(): List<String> = listOf(`in`)

    override fun getOutputFiles(): List<String> = emptyList()

    override fun run0() {
        val paResult: PaResult = try {
            PaResult.readJson(Paths.get(`in`))
        } catch (e: IOException) {
            throwValidationExceptionKotlin("Corrupted PA file.")
        }
        val result = paResult.results.first()
        println("CDR3 metrics:")
        result.printMetricsForGroup(CDR3Metrics)

        println()

        println("Diversity metrics:")
        result.printMetricsForGroup(Diversity)
    }

    private fun PaResultByGroup.printMetricsForGroup(groupName: String) {
        val group = schema.getGroup<CharacteristicGroup<Clone, *>>(groupName)
        result.forGroup(group).data.values
            .flatMap { chData ->
                chData.data.values.flatMap { metricsArray -> metricsArray.data.toList() }
            }
            .map { metricValue -> metricValue.key }
            .distinct()
            .forEach { metric -> println("    $metric") }
    }
}
