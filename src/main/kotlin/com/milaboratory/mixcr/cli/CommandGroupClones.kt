/*
 * Copyright (c) 2014-2023, MiLaboratories Inc. All Rights Reserved
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

import cc.redberry.pipe.OutputPort
import cc.redberry.pipe.util.flatten
import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.app.logger
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.mixcr.basictypes.ClnAReader
import com.milaboratory.mixcr.basictypes.ClnAWriter
import com.milaboratory.mixcr.basictypes.ClnsReader
import com.milaboratory.mixcr.basictypes.ClnsWriter
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.CloneSet
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.CLNA
import com.milaboratory.mixcr.basictypes.IOUtil.MiXCRFileType.CLNS
import com.milaboratory.mixcr.basictypes.MiXCRHeader
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.mixcr.clonegrouping.CloneGroupingParams.Companion.mkGrouper
import com.milaboratory.mixcr.clonegrouping.SingleCellGroupingParamsByOverlappingCellIds
import com.milaboratory.mixcr.presets.MiXCRCommandDescriptor
import com.milaboratory.mixcr.presets.MiXCRParamsBundle
import com.milaboratory.mixcr.util.Concurrency
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.SmartProgressReporter
import com.milaboratory.util.TempFileManager
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path

object CommandGroupClones {
    const val COMMAND_NAME = MiXCRCommandDescriptor.groupClones.name

    abstract class CmdBase : MiXCRCommandWithOutputs(), MiXCRPresetAwareCommand<CommandGroupClonesParams> {
        @Option(
            names = ["-O"],
            description = ["Overrides for the clone grouping parameters."],
            paramLabel = CommonDescriptions.Labels.OVERRIDES,
            order = OptionsOrder.overrides
        )
        private var overrides: Map<String, String> = mutableMapOf()

        override val paramsResolver =
            object : MiXCRParamsResolver<CommandGroupClonesParams>(MiXCRParamsBundle::groupClones) {
                override fun POverridesBuilderOps<CommandGroupClonesParams>.paramsOverrides() {
                    CommandGroupClonesParams::algorithm jsonOverrideWith overrides
                }
            }
    }

    @Command(
        description = ["Group clones by cells. Required data with cell tags."]
    )
    class Cmd : CmdBase() {
        @Parameters(
            description = ["Path to input file."],
            paramLabel = "clones.(clns|clna)",
            index = "0"
        )
        lateinit var inputFile: Path

        @Parameters(
            description = ["Path where to write output. Will have the same file type."],
            paramLabel = "grouped.(clns|clna)",
            index = "1"
        )
        lateinit var outputFile: Path

        @Mixin
        lateinit var reportOptions: ReportOptions

        @Mixin
        lateinit var resetPreset: ResetPresetOptions

        @Mixin
        lateinit var dontSavePresetOption: DontSavePresetOption

        @Mixin
        lateinit var useLocalTemp: UseLocalTempOption

        override val inputFiles
            get() = listOf(inputFile)

        override val outputFiles
            get() = listOf(outputFile)

        override fun validate() {
            ValidationException.requireTheSameFileType(
                inputFile,
                outputFile,
                InputFileType.CLNS,
                InputFileType.CLNA
            )
        }

        override fun run1() {
            val report = when (val fileType = IOUtil.extractFileType(inputFile)) {
                CLNS -> processClns()
                CLNA -> processClna()
                else -> throw ValidationException("Unsupported input type $fileType")
            }
            ReportUtil.writeReportToStdout(report)
            reportOptions.appendToFiles(report)
        }

        private fun processClns(): CloneGroupingReport =
            ClnsReader(inputFile, VDJCLibraryRegistry.getDefault()).use { reader ->
                val reportBuilder = CloneGroupingReport.Builder()

                val result = calculateGroupIdForClones(reader.readCloneSet(), reader.header, reportBuilder)

                logger.progress { "Writing output" }
                ClnsWriter(outputFile).use { writer ->
                    writer.writeCloneSet(result)
                    reportBuilder.setFinishMillis(System.currentTimeMillis())
                    val report = reportBuilder.buildReport()
                    writer.setFooter(reader.footer.addStepReport(MiXCRCommandDescriptor.groupClones, report))
                    report
                }
            }

        private fun processClna(): CloneGroupingReport =
            ClnAReader(inputFile, VDJCLibraryRegistry.getDefault(), Concurrency.noMoreThan(4)).use { reader ->
                val reportBuilder = CloneGroupingReport.Builder()

                val result = calculateGroupIdForClones(reader.readCloneSet(), reader.header, reportBuilder)

                val tempDest = TempFileManager.smartTempDestination(outputFile, "", !useLocalTemp.value)
                ClnAWriter(outputFile, tempDest).use { writer ->
                    var newNumberOfAlignments: Long = 0
                    val allAlignmentsList = mutableListOf<OutputPort<VDJCAlignments>>()
                    for (clone in result) {
                        newNumberOfAlignments += reader.numberOfAlignmentsInClone(clone.id)
                        allAlignmentsList += reader.readAlignmentsOfClone(clone.id)
                    }

                    logger.progress { "Writing output" }
                    writer.writeClones(result)
                    writer.collateAlignments(allAlignmentsList.flatten(), newNumberOfAlignments)
                    reportBuilder.setFinishMillis(System.currentTimeMillis())
                    val report = reportBuilder.buildReport()
                    writer.setFooter(reader.footer.addStepReport(MiXCRCommandDescriptor.groupClones, report))
                    writer.writeAlignmentsAndIndex()
                    report
                }
            }

        private fun calculateGroupIdForClones(
            input: CloneSet,
            header: MiXCRHeader,
            reportBuilder: CloneGroupingReport.Builder
        ): CloneSet {
            ValidationException.require(input.tagsInfo.hasTagsWithType(TagType.Cell)) {
                "Input doesn't have cell tags"
            }
            ValidationException.require(input.none { it.group != null }) {
                "Input file already grouped by cells"
            }

            val paramsSpec = resetPreset.overridePreset(header.paramsSpec)
            // val (_, cmdParams) = paramsResolver.resolve(paramsSpec)
            val (_, cmdParams) = 1 to CommandGroupClonesParams(
                SingleCellGroupingParamsByOverlappingCellIds(
                    minOverlapForSmaller = SingleCellGroupingParamsByOverlappingCellIds.Threshold(
                        0.8,
                        SingleCellGroupingParamsByOverlappingCellIds.Threshold.RoundingMode.UP
                    ),
                    minOverlapForBigger = SingleCellGroupingParamsByOverlappingCellIds.Threshold(
                        0.2,
                        SingleCellGroupingParamsByOverlappingCellIds.Threshold.RoundingMode.UP
                    ),
                    nonFunctional = SingleCellGroupingParamsByOverlappingCellIds.ForNonFunctional.DontProcess,
                    thresholdForAssigningLeftoverCells = SingleCellGroupingParamsByOverlappingCellIds.Threshold(
                        0.6,
                        SingleCellGroupingParamsByOverlappingCellIds.Threshold.RoundingMode.DOWN
                    )
                )
            )


            reportBuilder.setStartMillis(System.currentTimeMillis())
            reportBuilder.setInputFiles(inputFiles)
            reportBuilder.setOutputFiles(outputFiles)
            reportBuilder.commandLine = commandLineArguments

            val grouper = cmdParams.algorithm.mkGrouper<Clone>(
                input.tagsInfo,
                input.cloneSetInfo.assemblingFeatures
            )
            SmartProgressReporter.startProgressReport(grouper)
            val grouppedClones = grouper.groupClones(input.clones)
            reportBuilder.grouperReport = grouper.getReport()

            logger.progress { "Resorting and recalculating ranks" }
            return CloneSet.Builder(
                grouppedClones,
                input.usedGenes,
                input.header
                    .copy(calculatedCloneGroups = true)
                    .addStepParams(MiXCRCommandDescriptor.groupClones, cmdParams)
                    .copy(paramsSpec = dontSavePresetOption.presetToSave(paramsSpec))
            )
                .sort(input.ordering)
                // some clones split, need to recalculate
                .recalculateRanks()
                // total sums are not changed
                .withTotalCounts(input.counts)
                .build()
        }
    }
}
