/*
 * Copyright (c) 2014-2024, MiLaboratories Inc. All Rights Reserved
 *
 * Before downloading or accessing the software, please read carefully the
 * License Agreement available at:
 * https://github.com/milaboratory/mixcr/blob/develop/LICENSE
 *
 * By downloading or accessing the software, you accept and agree to be bound
 * by the terms of the License Agreement. If you do not want to agree to the terms
 * of the Licensing Agreement, you must not download or access the software.
 */
@file:Suppress("LocalVariableName")

package com.milaboratory.mixcr.cli

import cc.redberry.pipe.OutputPortFactory
import cc.redberry.pipe.util.filter
import cc.redberry.pipe.util.flatten
import cc.redberry.pipe.util.forEach
import cc.redberry.pipe.util.map
import cc.redberry.pipe.util.mapInParallelOrdered
import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.mixcr.assembler.CloneFactory
import com.milaboratory.mixcr.assembler.fullseq.CoverageAccumulator
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssembler
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssemblerParameters
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssemblerReportBuilder
import com.milaboratory.mixcr.assembler.fullseq.PostFiltering
import com.milaboratory.mixcr.basictypes.ClnAReader
import com.milaboratory.mixcr.basictypes.ClnAReader.CloneAlignments
import com.milaboratory.mixcr.basictypes.ClnsWriter
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.CloneSet
import com.milaboratory.mixcr.basictypes.HasFeatureToAlign
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.MiXCRFooter
import com.milaboratory.mixcr.basictypes.MiXCRHeader
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.VDJCSProperties.CloneOrdering
import com.milaboratory.mixcr.basictypes.tag.TagCountAggregator
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.mixcr.basictypes.validateCompositeFeatures
import com.milaboratory.mixcr.cli.CommonDescriptions.DEFAULT_VALUE_FROM_PRESET
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.presets.AnalyzeCommandDescriptor
import com.milaboratory.mixcr.presets.MiXCRParamsBundle
import com.milaboratory.mixcr.presets.MiXCRParamsSpec
import com.milaboratory.mixcr.util.Concurrency
import com.milaboratory.primitivio.PipeDataInputReader
import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.SmartProgressReporter
import io.repseq.core.GeneFeature
import io.repseq.core.GeneFeatures
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.VDJCGene
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine.Command
import picocli.CommandLine.Mixin
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.file.Path
import java.util.*

object CommandAssembleContigs {
    const val COMMAND_NAME = AnalyzeCommandDescriptor.assembleContigs.name

    abstract class CmdBase : MiXCRCommandWithOutputs(), MiXCRPresetAwareCommand<CommandAssembleContigsParams> {
        @Option(
            description = [
                "Ignore tags (UMIs, cell-barcodes).",
                DEFAULT_VALUE_FROM_PRESET
            ],
            names = ["--ignore-tags"],
            order = OptionsOrder.main + 10_100
        )
        private var ignoreTags = false

        @Option(
            names = ["-O"],
            description = ["Overrides for the assembler parameters."],
            paramLabel = Labels.OVERRIDES,
            order = OptionsOrder.overrides
        )
        private var overrides: Map<String, String> = mutableMapOf()

        @Mixin
        private var mixins: AssembleContigsMiXCRMixins? = null

        protected val mixinsToAdd get() = mixins?.mixins ?: emptyList()

        override val paramsResolver =
            object : MiXCRParamsResolver<CommandAssembleContigsParams>(MiXCRParamsBundle::assembleContigs) {
                override fun POverridesBuilderOps<CommandAssembleContigsParams>.paramsOverrides() {
                    CommandAssembleContigsParams::ignoreTags setIfTrue ignoreTags
                    CommandAssembleContigsParams::parameters jsonOverrideWith overrides
                }

                override fun validateParams(params: CommandAssembleContigsParams) {
                    if (params.parameters.postFiltering != PostFiltering.NoFiltering) {
                        if (params.parameters.assemblingRegions == null) {
                            throw ValidationException("assemblingRegion must be set if postFiltering is not NoFiltering")
                        }
                    }
                }
            }
    }

    @Command(
        description = ["Assemble full sequences."]
    )
    class Cmd : CmdBase() {
        @Parameters(
            description = ["Path to input clna file"],
            paramLabel = "clones.clna",
            index = "0"
        )
        lateinit var inputFile: Path

        @Parameters(
            description = ["Path where to write assembled clones."],
            paramLabel = "clones.clns",
            index = "1"
        )
        lateinit var outputFile: Path

        @Mixin
        lateinit var threadsOption: ThreadsOption

        @Mixin
        lateinit var reportOptions: ReportOptions

        @Option(description = ["Report file."], names = ["--debug-report"], hidden = true)
        var debugReportFile: Path? = null

        @Mixin
        lateinit var resetPreset: ResetPresetOptions

        @Mixin
        lateinit var dontSavePresetOption: DontSavePresetOption

        override val inputFiles get() = listOf(inputFile)

        override val outputFiles get() = listOf(outputFile)

        override fun validate() {
            ValidationException.requireFileType(inputFile, InputFileType.CLNA)
            ValidationException.requireFileType(outputFile, InputFileType.CLNS)
        }

        override fun run1() {
            val beginTimestamp = System.currentTimeMillis()

            val cmdParams: CommandAssembleContigsParams
            val paramsSpec: MiXCRParamsSpec
            val assemblingRegions: GeneFeatures?

            val reportBuilder = FullSeqAssemblerReportBuilder()
            var totalClonesCount = 0
            val genes: List<VDJCGene>
            val header: MiXCRHeader
            val ordering: CloneOrdering
            val footer: MiXCRFooter

            ClnAReader(inputFile, VDJCLibraryRegistry.getDefault(), Concurrency.noMoreThan(4)).use { reader ->
                paramsSpec = resetPreset.overridePreset(reader.header.paramsSpec).addMixins(mixinsToAdd)
                cmdParams = paramsResolver.resolve(paramsSpec).second
                assemblingRegions = cmdParams.parameters.assemblingRegions

                validateParams(cmdParams, reader.header)

                ValidationException.require(reader.assemblingFeatures.size == 1) {
                    "Supports only singular assemblingFeature."
                }
                val assemblingFeature = reader.assemblingFeatures.first()
                ValidationException.require(!assemblingFeature.isComposite) {
                    "Supports only non-composite gene features as an assemblingFeature."
                }

                if (assemblingRegions != null) {
                    val fullyIncluded = assemblingRegions.features.any { assemblingRegion ->
                        GeneFeature.intersection(assemblingRegion, assemblingFeature) == assemblingFeature
                    }
                    ValidationException.require(fullyIncluded) {
                        "AssemblingFeature of input must be included fully in assemblingRegions"
                    }
                }

                PrimitivO(BufferedOutputStream(FileOutputStream(outputFile.toFile()))).use { tmpOut ->
                    debugReportFile?.let { BufferedWriter(OutputStreamWriter(FileOutputStream(it.toFile()))) }
                        .use { debugReport ->
                            footer = reader.footer
                            ordering = reader.ordering
                            header = reader.header
                            genes = reader.usedGenes

                            IOUtil.registerGeneReferences(tmpOut, genes, header)
                            val cloneAlignmentsPort = reader.clonesAndAlignments()
                            SmartProgressReporter.startProgressReport("Assembling contigs", cloneAlignmentsPort)

                            val assembler = Assembler(
                                reader,
                                reportBuilder,
                                cmdParams.parameters,
                                debugReport,
                                debugReportFile
                            )

                            val parallelProcessor = cloneAlignmentsPort.mapInParallelOrdered(
                                threadsOption.value,
                                bufferSize = 1024
                            ) { cloneAlignments: CloneAlignments ->
                                val clone = when {
                                    cmdParams.ignoreTags -> cloneAlignments.clone.removeInfoAboutTags()
                                    else -> cloneAlignments.clone
                                }
                                try {
                                    assembler.assembleContigs(clone, OutputPortFactory { cloneAlignments.alignments() })
                                } catch (re: Throwable) {
                                    throw RuntimeException("While processing clone #" + clone.id, re)
                                }
                            }
                            parallelProcessor.forEach { clones ->
                                totalClonesCount += clones.size
                                for (cl in clones) {
                                    println("------------")
                                    println("${cl.getBestHit(Variable)}:${cl.getBestHit(Joining)}")
                                    println(cl.tagCount)
                                    tmpOut.writeObject(cl)
                                }
                            }
                            // in the case of cells, initial clones will be split by cell barcodes.
                            // assert(reportBuilder.initialCloneCount == reader.numberOfClones())
                        }
                }
            }
            assert(reportBuilder.finalCloneCount == totalClonesCount)
            // assert(
            //     cmdParams.parameters.postFiltering == PostFiltering.OnlyFullyDefined ||
            //             cmdParams.parameters.postFiltering == PostFiltering.OnlyFullyAssembled ||
            //             reportBuilder.finalCloneCount >= reportBuilder.initialCloneCount
            // )
            val clones: MutableList<Clone> = ArrayList(totalClonesCount)
            PrimitivI(BufferedInputStream(FileInputStream(outputFile.toFile()))).use { tmpIn ->
                IOUtil.registerGeneReferences(tmpIn, genes, header)
                var cloneId = 0
                PipeDataInputReader(Clone::class.java, tmpIn, totalClonesCount.toLong()).forEach { clone ->
                    clones += clone.withId(cloneId++)
                }
            }
            val allFullyCoveredBy = cmdParams.parameters.allClonesWillBeCoveredByFeature()
            val resultHeader = header
                .copy(allFullyCoveredBy = if (allFullyCoveredBy) assemblingRegions else null)
                .addStepParams(AnalyzeCommandDescriptor.assembleContigs, cmdParams)
                .copy(paramsSpec = dontSavePresetOption.presetToSave(paramsSpec))

            val cloneSet = CloneSet.Builder(clones, genes, resultHeader)
                .sort(ordering)
                .recalculateRanks()
                .calculateTotalCounts()
                .build()
            ClnsWriter(outputFile).use { writer ->
                writer.writeCloneSet(cloneSet)
                reportBuilder.setStartMillis(beginTimestamp)
                reportBuilder.setInputFiles(inputFile)
                reportBuilder.setOutputFiles(outputFile)
                reportBuilder.commandLine = commandLineArguments
                reportBuilder.setFinishMillis(System.currentTimeMillis())
                val report = reportBuilder.buildReport()
                // Writing report to stout
                ReportUtil.writeReportToStdout(report)
                reportOptions.appendToFiles(report)
                writer.setFooter(footer.addStepReport(AnalyzeCommandDescriptor.assembleContigs, report))
            }
        }
    }

    fun validateParams(
        cmdParams: CommandAssembleContigsParams,
        featuresToAlign: HasFeatureToAlign
    ) {
        @Suppress("DEPRECATION")
        listOfNotNull(
            cmdParams.parameters.subCloningRegions,
            cmdParams.parameters.assemblingRegions,
            when (val postFiltering = cmdParams.parameters.postFiltering) {
                is PostFiltering.MinimalContigLength -> null
                PostFiltering.NoFiltering -> null
                is PostFiltering.OnlyCovering -> postFiltering.geneFeatures
                PostFiltering.OnlyFullyAssembled -> null
                PostFiltering.OnlyFullyDefined -> null
                is PostFiltering.OnlyUnambiguouslyCovering -> postFiltering.geneFeatures
            }
        ).forEach { featuresToAlign.validateCompositeFeatures(it) }
    }
}

private class Assembler(
    private val reader: ClnAReader,
    private val reportBuilder: FullSeqAssemblerReportBuilder,
    private val parameters: FullSeqAssemblerParameters,
    private val debugReport: BufferedWriter?,
    private val debugReportFile: Path?
) {

    private val cloneFactory = CloneFactory(
        reader.assemblerParameters.cloneFactoryParameters,
        reader.assemblingFeatures,
        reader.usedGenes,
        reader.header.featuresToAlignMap
    )

    private val header get() = reader.header

    fun assembleContigs(originalClone: Clone, alignmentsPort: OutputPortFactory<VDJCAlignments>): Array<Clone> =
        if (reader.header.tagsInfo.hasTagsWithType(TagType.Cell)) {
            val cellTageLevel = reader.header.tagsInfo.getDepthFor(TagType.Cell)
            // process every cell barcode separately
            originalClone.tagCount.splitBy(cellTageLevel)
                .flatMap { tagsFromCell ->
                    val cellBarcode = tagsFromCell.reduceToLevel(cellTageLevel).singletonTuple
                    assembleContigs0(
                        originalClone.withTagCount(tagsFromCell),
                        OutputPortFactory {
                            // use alignments only from this cell barcode
                            alignmentsPort.createPort()
                                .filter { alignments ->
                                    alignments.tagCount.allTagsHasPrefix(cellBarcode)
                                }
                                .map { alignments ->
                                    alignments.tagCount.splitBy(cellTageLevel).map { tags ->
                                        alignments.withTagCount(tags)
                                    }
                                }
                                .flatten()
                        }
                    ).toList()
                }
                .groupBy { clone ->
                    // in the case of the same clones from different cells, we should combine clones back by target+isotype
                    val key = mutableListOf<Any?>()
                    for (i in 0 until clone.numberOfTargets()) {
                        key += clone.getTarget(i).sequence
                    }
                    key += clone.isotypeSubclass ?: clone.isotype
                    key
                }
                .values.map { clones ->
                    if (clones.size == 1) return@map clones.first()
                    reportBuilder.onClonesCombination(clones)
                    val allTags = clones
                        .fold(TagCountAggregator()) { agg, clone -> agg.add(clone.tagCount) }
                        .createAndDestroy()
                    clones.first().withTagCount(allTags)
                }
                .toTypedArray()
        } else {
            assembleContigs0(originalClone, alignmentsPort)
        }

    private fun assembleContigs0(
        originalClone: Clone,
        alignmentsPort: OutputPortFactory<VDJCAlignments>
    ): Array<Clone> {
        // Collecting statistics
        val coverages = originalClone.hitsMap
            .filterValues { value -> value != null && value.isNotEmpty() }
            .mapValues { (_, value) ->
                value
                    .filter { hit ->
                        hit.geneType !in GeneType.VJ_REFERENCE ||
                                FullSeqAssembler.checkGeneCompatibility(hit, reader.assemblingFeatures)
                    }
                    .associate { hit -> hit.gene.id to CoverageAccumulator(hit) }
            }
            // Filtering empty maps
            .filterValues { it.isNotEmpty() }
            .toMap(EnumMap(GeneType::class.java))
        if (Variable !in coverages || Joining !in coverages) {
            // Something went really wrong
            reportBuilder.onAssemblyCanceled(originalClone)
            return arrayOf(originalClone)
        }
        alignmentsPort.createPort().forEach { alignments ->
            for ((key, value) in alignments.hitsMap) {
                for (hit in value) {
                    coverages[key]?.let { it[hit.gene.id]?.accumulate(hit) }
                }
            }
        }

        // Selecting best hits for clonal sequence assembly based in the coverage information
        val bestGenes = coverages.mapValuesTo(EnumMap(GeneType::class.java)) { (_, value) ->
            value.values.maxByOrNull { it.getNumberOfCoveredPoints(1) }?.hit
        }

        // Performing contig assembly
        val fullSeqAssembler = FullSeqAssembler(
            cloneFactory, parameters,
            header.assemblerParameters!!.assemblingFeatures,
            originalClone, header.alignerParameters,
            bestGenes[Variable], bestGenes[Joining]
        )
        fullSeqAssembler.report = reportBuilder
        val rawVariantsData = fullSeqAssembler.calculateRawData { alignmentsPort.createPort() }

        if (debugReport != null) {
            synchronized(debugReport) {
                FileOutputStream(debugReportFile!!.toString() + "." + originalClone.id).use { fos ->
                    val content = rawVariantsData.toCsv(10.toByte())
                    fos.write(content.toByteArray())
                }
                debugReport.write("Clone: " + originalClone.id)
                debugReport.newLine()
                debugReport.write(rawVariantsData.toString())
                debugReport.newLine()
                debugReport.newLine()
                debugReport.write("==========================================")
                debugReport.newLine()
                debugReport.newLine()
            }
        }

        return fullSeqAssembler.callVariants(rawVariantsData)
    }
}
