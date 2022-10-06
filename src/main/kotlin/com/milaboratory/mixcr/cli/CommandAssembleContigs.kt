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
@file:Suppress("LocalVariableName")

package com.milaboratory.mixcr.cli

import cc.redberry.pipe.CUtils
import com.fasterxml.jackson.annotation.JsonMerge
import com.fasterxml.jackson.annotation.JsonProperty
import com.milaboratory.cli.POverridesBuilderOps
import com.milaboratory.mixcr.MiXCRCommand
import com.milaboratory.mixcr.MiXCRParams
import com.milaboratory.mixcr.MiXCRParamsBundle
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
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.MiXCRFooter
import com.milaboratory.mixcr.basictypes.MiXCRHeader
import com.milaboratory.mixcr.basictypes.VDJCSProperties.CloneOrdering
import com.milaboratory.mixcr.basictypes.tag.TagCount
import com.milaboratory.mixcr.basictypes.tag.TagTuple
import com.milaboratory.mixcr.util.Concurrency
import com.milaboratory.primitivio.PipeDataInputReader
import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.forEach
import com.milaboratory.primitivio.mapInParallelOrdered
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.SmartProgressReporter
import com.milaboratory.util.StreamUtil
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.VDJCGene
import io.repseq.core.VDJCGeneId
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.*
import java.util.stream.Collectors

object CommandAssembleContigs {
    const val COMMAND_NAME = "assembleContigs"

    data class Params(
        @JsonProperty("ignoreTags") val ignoreTags: Boolean,
        @JsonProperty("parameters") @JsonMerge val parameters: FullSeqAssemblerParameters
    ) : MiXCRParams {
        override val command = MiXCRCommand.assembleContigs
    }

    abstract class CmdBase : MiXCRPresetAwareCommand<Params>() {
        @Option(description = ["Ignore tags (UMIs, cell-barcodes)"], names = ["--ignore-tags"])
        private var ignoreTags = false

        @Option(names = ["-O"], description = ["Overrides for the assembler parameters."])
        private var overrides: Map<String, String> = mutableMapOf()

        @ArgGroup(validate = false, heading = "Assemble contig mix-ins", exclusive = false)
        private var mixins: AllAssembleContigsMiXCRMixins? = null

        protected val mixinsToAdd get() = mixins?.mixins ?: emptyList()

        override val paramsResolver = object : MiXCRParamsResolver<Params>(this, MiXCRParamsBundle::assembleContigs) {
            override fun POverridesBuilderOps<Params>.paramsOverrides() {
                Params::ignoreTags setIfTrue ignoreTags
                Params::parameters jsonOverrideWith overrides
            }

            override fun validateParams(params: Params) {
                if (params.parameters.postFiltering != PostFiltering.NoFiltering) {
                    if (params.parameters.assemblingRegions != null) {
                        throwValidationExceptionKotlin("assemblingRegion must be set if postFiltering is not NoFiltering")
                    }
                }
            }
        }
    }

    @Command(
        name = COMMAND_NAME,
        sortOptions = true,
        separator = " ",
        description = ["Assemble full sequences."]
    )
    class Cmd : CmdBase() {
        @Parameters(description = ["clones.clna"], index = "0")
        lateinit var inputFile: String

        @Parameters(description = ["clones.clns"], index = "1")
        lateinit var outputFile: String

        @Option(description = ["Processing threads"], names = ["-t", "--threads"])
        var threads = Runtime.getRuntime().availableProcessors()
            set(value) {
                if (value <= 0) throwValidationExceptionKotlin("-t / --threads must be positive")
                field = value
            }

        @Option(description = [CommonDescriptions.REPORT], names = ["-r", "--report"])
        var reportFile: String? = null

        @Option(description = ["Report file."], names = ["--debug-report"], hidden = true)
        var debugReportFile: String? = null

        @Option(description = [CommonDescriptions.JSON_REPORT], names = ["-j", "--json-report"])
        var jsonReport: String? = null

        override fun getInputFiles() = listOf(inputFile)

        override fun getOutputFiles() = listOf(outputFile)

        override fun run0() {
            val beginTimestamp = System.currentTimeMillis()

            val cmdParams: Params

            val reportBuilder = FullSeqAssemblerReportBuilder()
            var totalClonesCount = 0
            val genes: List<VDJCGene>
            val header: MiXCRHeader
            val ordering: CloneOrdering
            val footer: MiXCRFooter

            ClnAReader(inputFile, VDJCLibraryRegistry.getDefault(), Concurrency.noMoreThan(4)).use { reader ->
                cmdParams = paramsResolver.resolve(reader.header.paramsSpec.addMixins(mixinsToAdd)).second

                require(reader.assemblingFeatures.size == 1) {
                    "Supports only singular assemblingFeature."
                }
                val assemblingFeature = reader.assemblingFeatures.first()
                require(!assemblingFeature.isComposite) {
                    "Supports only non-composite gene features as an assemblingFeature."
                }

                cmdParams.parameters.assemblingRegions?.let { assemblingRegions ->
                    val fullyIncluded = assemblingRegions.features.any { assemblingRegion ->
                        GeneFeature.intersection(assemblingRegion, assemblingFeature) == assemblingFeature
                    }
                    require(fullyIncluded) {
                        "AssemblingFeature of input must be included fully in assemblingRegions"
                    }
                }

                PrimitivO(BufferedOutputStream(FileOutputStream(outputFile))).use { tmpOut ->
                    debugReportFile?.let { BufferedWriter(OutputStreamWriter(FileOutputStream(it))) }
                        .use { debugReport ->
                            footer = reader.footer
                            ordering = reader.ordering()
                            val cloneFactory = CloneFactory(
                                reader.assemblerParameters.cloneFactoryParameters,
                                reader.assemblingFeatures,
                                reader.usedGenes,
                                reader.alignerParameters.featuresToAlignMap
                            )
                            header = reader.header
                            genes = reader.usedGenes

                            IOUtil.registerGeneReferences(tmpOut, genes, header.alignerParameters)
                            val cloneAlignmentsPort = reader.clonesAndAlignments()
                            SmartProgressReporter.startProgressReport("Assembling contigs", cloneAlignmentsPort)
                            val parallelProcessor = cloneAlignmentsPort.mapInParallelOrdered(
                                threads,
                                bufferSize = 1024
                            ) { cloneAlignments: CloneAlignments ->
                                val clone = when {
                                    cmdParams.ignoreTags -> cloneAlignments.clone
                                        .setTagCount(
                                            TagCount(
                                                TagTuple.NO_TAGS,
                                                cloneAlignments.clone.tagCount.sum()
                                            )
                                        )

                                    else -> cloneAlignments.clone
                                }
                                try {
                                    // Collecting statistics
                                    var coverages = clone.hitsMap
                                        .entries.stream()
                                        .filter { (_, value) -> value != null && value.isNotEmpty() }
                                        .collect(
                                            Collectors.toMap(
                                                { (key, _) -> key },
                                                { (_, value) ->
                                                    Arrays.stream(
                                                        value
                                                    )
                                                        .filter { h ->
                                                            h.geneType != Variable && h.geneType != Joining ||
                                                                    FullSeqAssembler.checkGeneCompatibility(
                                                                        h,
                                                                        reader.assemblingFeatures
                                                                    )
                                                        }
                                                        .collect(
                                                            Collectors.toMap(
                                                                { h -> h.gene.id },
                                                                { hit -> CoverageAccumulator(hit) })
                                                        )
                                                },
                                                StreamUtil.noMerge(),
                                                { EnumMap(GeneType::class.java) }
                                            )
                                        )

                                    // Filtering empty maps
                                    coverages = coverages.entries.stream()
                                        .filter { (_, value) -> value.isNotEmpty() }
                                        .collect(
                                            Collectors.toMap(
                                                { (key, _) -> key },
                                                { (_, value) -> value },
                                                StreamUtil.noMerge(),
                                                { EnumMap(GeneType::class.java) }
                                            )
                                        )
                                    if (!coverages.containsKey(Variable) || !coverages.containsKey(Joining)) {
                                        // Something went really wrong
                                        reportBuilder.onAssemblyCanceled(clone)
                                        return@mapInParallelOrdered arrayOf(clone)
                                    }
                                    for (alignments in CUtils.it(cloneAlignments.alignments())) {
                                        for ((key, value) in alignments.hitsMap) {
                                            for (hit in value) {
                                                Optional.ofNullable(coverages[key])
                                                    .flatMap { Optional.ofNullable(it[hit.gene.id]) }
                                                    .ifPresent { acc -> acc.accumulate(hit) }
                                            }
                                        }
                                    }

                                    // Selecting best hits for clonal sequence assembly based in the coverage information
                                    val bestGenes = coverages.entries.stream()
                                        .collect(
                                            Collectors.toMap(
                                                { (key, _) -> key },
                                                { (_, value) ->
                                                    value.entries.stream()
                                                        .max(Comparator.comparing { (_, value1): Map.Entry<VDJCGeneId?, CoverageAccumulator> ->
                                                            value1.getNumberOfCoveredPoints(1)
                                                        })
                                                        .map { (_, value1) -> value1.hit }
                                                        .get()
                                                },
                                                StreamUtil.noMerge(),
                                                { EnumMap(GeneType::class.java) })
                                        )

                                    // Performing contig assembly
                                    val fullSeqAssembler = FullSeqAssembler(
                                        cloneFactory, cmdParams.parameters,
                                        clone, header.alignerParameters,
                                        bestGenes[Variable], bestGenes[Joining]
                                    )
                                    fullSeqAssembler.report = reportBuilder
                                    val rawVariantsData =
                                        fullSeqAssembler.calculateRawData { cloneAlignments.alignments() }
                                    if (debugReport != null) {
                                        @Suppress("BlockingMethodInNonBlockingContext")
                                        synchronized(debugReport) {
                                            FileOutputStream(debugReportFile + "." + clone.id).use { fos ->
                                                val content = rawVariantsData.toCsv(10.toByte())
                                                fos.write(content.toByteArray())
                                            }
                                            debugReport.write("Clone: " + clone.id)
                                            debugReport.newLine()
                                            debugReport.write(rawVariantsData.toString())
                                            debugReport.newLine()
                                            debugReport.newLine()
                                            debugReport.write("==========================================")
                                            debugReport.newLine()
                                            debugReport.newLine()
                                        }
                                    }

                                    return@mapInParallelOrdered fullSeqAssembler.callVariants(rawVariantsData)
                                } catch (re: Throwable) {
                                    throw RuntimeException("While processing clone #" + clone.id, re)
                                }
                            }
                            parallelProcessor.forEach { clones ->
                                totalClonesCount += clones.size
                                for (cl in clones) {
                                    tmpOut.writeObject(cl)
                                }
                            }
                            assert(reportBuilder.initialCloneCount == reader.numberOfClones())
                        }
                }
            }
            assert(reportBuilder.finalCloneCount == totalClonesCount)
            assert(reportBuilder.finalCloneCount >= reportBuilder.initialCloneCount)
            var cloneId = 0
            val clones = arrayOfNulls<Clone>(totalClonesCount)
            PrimitivI(BufferedInputStream(FileInputStream(outputFile))).use { tmpIn ->
                IOUtil.registerGeneReferences(tmpIn, genes, header.alignerParameters)
                var i = 0
                PipeDataInputReader(Clone::class.java, tmpIn, totalClonesCount.toLong()).forEach { clone ->
                    clones[i++] = clone.setId(cloneId++)
                }
            }
            val resultHeader = if (
                cmdParams.parameters.assemblingRegions != null &&
                cmdParams.parameters.subCloningRegions == cmdParams.parameters.assemblingRegions &&
                cmdParams.parameters.postFiltering == PostFiltering.OnlyFullyDefined
            ) {
                header.copy(allFullyCoveredBy = cmdParams.parameters.assemblingRegions)
            } else {
                header
            }

            val cloneSet = CloneSet(listOf(*clones), genes, resultHeader, footer, ordering)
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
                if (reportFile != null) ReportUtil.appendReport(reportFile, report)
                if (jsonReport != null) ReportUtil.appendJsonReport(jsonReport, report)
                writer.setFooter(footer.addReport(report))
            }
        }
    }
}

