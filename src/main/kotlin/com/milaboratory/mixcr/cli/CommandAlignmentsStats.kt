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
package com.milaboratory.mixcr.cli

import cc.redberry.pipe.CUtils
import cc.redberry.pipe.VoidProcessor
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import com.milaboratory.mixcr.info.AlignmentInfoCollector
import com.milaboratory.mixcr.info.GeneFeatureCoverageCollector
import com.milaboratory.mixcr.info.ReferencePointCoverageCollector
import com.milaboratory.util.SmartProgressReporter
import io.repseq.core.GeneFeature
import io.repseq.core.ReferencePoint
import picocli.CommandLine
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.PrintStream
import kotlin.math.min

private val targetFeatures = arrayOf(
    GeneFeature.V5UTR,
    GeneFeature(ReferencePoint.L1Begin, -20, 0),
    GeneFeature.L1,
    GeneFeature.VIntron,
    GeneFeature.L2,
    GeneFeature.FR1,
    GeneFeature.CDR1,
    GeneFeature.FR2,
    GeneFeature.CDR2,
    GeneFeature.FR3,
    GeneFeature.CDR3,
    GeneFeature.FR4,
    GeneFeature(GeneFeature.FR4, 0, -3)
)
private val targetReferencePoints = arrayOf(
    ReferencePoint.L1Begin,
    ReferencePoint.L1End,
    ReferencePoint.L2Begin,
    ReferencePoint.FR1Begin,
    ReferencePoint.CDR1Begin,
    ReferencePoint.FR2Begin,
    ReferencePoint.CDR2Begin,
    ReferencePoint.FR3Begin,
    ReferencePoint.CDR3Begin,
    ReferencePoint.FR4Begin,
    ReferencePoint.FR4End
)

@CommandLine.Command(
    name = "alignmentsStat",
    sortOptions = true,
    hidden = true,
    separator = " ",
    description = ["Alignments statistics."]
)
class CommandAlignmentsStats : MiXCRCommand() {
    @CommandLine.Parameters(index = "0", description = ["input_file.vdjca"])
    lateinit var `in`: String

    @CommandLine.Parameters(index = "1", description = ["[output.txt]"], arity = "0..1")
    var out: String? = null

    override fun getInputFiles(): List<String> = listOf(`in`)

    override fun getOutputFiles(): List<String> = listOfNotNull(out)

    override fun run0() {
        val collectors = targetFeatures.map { GeneFeatureCoverageCollector(it) } +
                targetReferencePoints.map { ReferencePointCoverageCollector(it, 40, 40) }

        val collector = Collector(collectors)
        VDJCAlignmentsReader(`in`).use { reader ->
            (out?.let { PrintStream(BufferedOutputStream(FileOutputStream(it), 32768)) } ?: System.out).use { output ->
                SmartProgressReporter.startProgressReport("Analysis", reader)
                CUtils.processAllInParallel(reader, collector, min(4, Runtime.getRuntime().availableProcessors()))
                collector.end()
                if (output === System.out) output.println()
                collector.write(output)
            }
        }
    }

    private class Collector(val collectors: List<AlignmentInfoCollector>) : VoidProcessor<VDJCAlignments> {
        override fun process(input: VDJCAlignments) = collectors.forEach { collector ->
            collector.put(input)
        }

        fun end() = collectors.forEach { collector ->
            collector.end()
        }

        fun write(writer: PrintStream) = collectors.forEach { collector ->
            collector.writeResult(writer)
        }
    }
}
