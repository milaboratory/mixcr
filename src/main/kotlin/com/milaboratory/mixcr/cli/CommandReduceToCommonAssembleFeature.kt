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
package com.milaboratory.mixcr.cli

import cc.redberry.pipe.CUtils
import cc.redberry.pipe.util.buffered
import cc.redberry.pipe.util.filter
import cc.redberry.pipe.util.map
import cc.redberry.pipe.util.mapInParallel
import cc.redberry.pipe.util.toList
import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.app.logger
import com.milaboratory.core.alignment.Alignment
import com.milaboratory.mixcr.assembler.CloneFactory
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssembler
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssemblerParameters
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssemblerReportBuilder
import com.milaboratory.mixcr.basictypes.ClnsWriter
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.CloneSet
import com.milaboratory.mixcr.basictypes.CloneSetIO
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.VDJCHit
import com.milaboratory.mixcr.basictypes.tag.TagCount
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.presets.AssembleContigsMixins
import com.milaboratory.mixcr.presets.MiXCRCommandDescriptor
import com.milaboratory.mixcr.trees.constructStateBuilder
import com.milaboratory.mixcr.util.VJPair
import com.milaboratory.mixcr.util.plus
import com.milaboratory.util.ComparatorWithHash
import com.milaboratory.util.FormatUtils
import com.milaboratory.util.JsonOverrider
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.TempFileDest
import com.milaboratory.util.TempFileManager
import com.milaboratory.util.groupByOnDisk
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.GeneFeature.VDJRegion
import io.repseq.core.GeneFeatures
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Constant
import io.repseq.core.GeneType.Diversity
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.ReferencePoint.CDR3Begin
import io.repseq.core.ReferencePoint.CDR3End
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine
import picocli.CommandLine.Command
import picocli.CommandLine.Help.Visibility.ALWAYS
import picocli.CommandLine.Mixin
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.atomic.LongAdder
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries

@Command(
    description = [
        "Reassemble clones so, that all outputs will have the same assemble feature, thus could be used for inferring alleles and building SHM trees."
    ]
)
class CommandReduceToCommonAssembleFeature : MiXCRCommandWithOutputs() {
    @Parameters(
        index = "0",
        arity = "2..*",
        paramLabel = "$inputsLabel $outputLabel",
        hideParamSyntax = true,
        // help is covered by mkCommandSpec
        hidden = true
    )
    val inOut: List<String> = mutableListOf()

    @Option(
        names = ["--maximum-gene-feature"],
        required = false,
        paramLabel = Labels.GENE_FEATURE,
        description = ["Limit result intersected feature by this."],
        showDefaultValue = ALWAYS
    )
    var maximumGeneFeature: GeneFeatures = GeneFeatures(VDJRegion)

    @Mixin
    lateinit var threadsOptions: ThreadsOption

    @Mixin
    lateinit var useLocalTemp: UseLocalTempOption

    @Mixin
    lateinit var reportOptions: ReportOptions

    @Option(
        names = ["-O"],
        description = ["Overrides default find alleles parameter values"],
        paramLabel = Labels.OVERRIDES,
        order = OptionsOrder.overrides
    )
    var overrides: Map<String, String> = mutableMapOf()

    private val outputTemplate get() = inOut.last()

    override val inputFiles: List<Path>
        get() = inOut.dropLast(1).map { Paths.get(it) }.flatMap { path ->
            when {
                path.isDirectory() -> path.listDirectoryEntries()
                else -> listOf(path)
            }
        }

    override val outputFiles: List<Path> by lazy {
        OutputTemplate.calculateOutputs(outputTemplate, inputFiles)
    }

    private val tempDest: TempFileDest by lazy {
        val path = outputFiles.first()
        if (useLocalTemp.value) path.toAbsolutePath().parent.createDirectories()
        TempFileManager.smartTempDestination(path, ".cutToCommonFeature", !useLocalTemp.value)
    }

    private fun assemblerParameters(targetFeature: GeneFeatures): FullSeqAssemblerParameters {
        val presetName = "default"
        var result = FullSeqAssemblerParameters.presets.getByName(presetName)
            ?: throw ValidationException("Unknown parameters: $presetName")
        if (overrides.isNotEmpty()) {
            result = JsonOverrider.override(result, FullSeqAssemblerParameters::class.java, overrides)
                ?: throw ValidationException("Failed to override some parameter: $overrides")
        }
        return result.withAllCoveredByFeature(targetFeature)
    }

    override fun validate() {
        ValidationException.requireNotEmpty(inputFiles) { "There is no files to process" }
        inputFiles.forEach { input ->
            ValidationException.requireFileType(input, InputFileType.CLNX)
        }
        if (!outputTemplate.endsWith(".clns")) {
            throw ValidationException("Wrong template: command produces only clns, got $outputTemplate")
        }
    }

    override fun run1() {
        val begin = Instant.now()

        val libraryRegistry = VDJCLibraryRegistry.getDefault()
        val datasets = inputFiles.map { CloneSetIO.mkReader(it, libraryRegistry) }
        ValidationException.require(datasets.all { it.header.allFullyCoveredBy != null }) {
            val withoutFullyCovered = datasets.withIndex()
                .filter { it.value.header.allFullyCoveredBy == null }
                .map { inputFiles[it.index] }
                .joinToString(", ")
            "Some of the inputs were processed by `${CommandAssembleContigs.COMMAND_NAME}` or `${CommandAnalyze.COMMAND_NAME}` without `${AssembleContigsMixins.SetContigAssemblingFeatures.CMD_OPTION}` option: $withoutFullyCovered"
        }

        ValidationException.require(datasets.all { it.header.allFullyCoveredBy != GeneFeatures(CDR3) }) {
            val withoutCoveredCDR3 = datasets.withIndex()
                .filter { it.value.header.allFullyCoveredBy == GeneFeatures(CDR3) }
                .map { inputFiles[it.index] }
                .joinToString(", ")
            "Assemble feature must cover more than CDR3 in files: $withoutCoveredCDR3"
        }

        ValidationException.require(datasets.all { it.header.allFullyCoveredBy!!.intersection(CDR3) != null }) {
            val withoutCoveredCDR3 = datasets.withIndex()
                .filter { it.value.header.allFullyCoveredBy!!.intersection(CDR3) == null }
                .map { inputFiles[it.index] }
                .joinToString(", ")
            "Assemble feature must contain CDR3 in files: $withoutCoveredCDR3"
        }

        val recalculateScores = GeneType.VJ_REFERENCE.any { geneType ->
            val scores = datasets.map {
                it.assemblerParameters.cloneFactoryParameters.getVJCParameters(geneType).scoring
            }
            scores.distinct().size != 1
        }
        if (recalculateScores) {
            logger.warn {
                "Input files have different scoring for V or J genes. " +
                        "Hit scores will be recalculated for all clones, that will lead for loosing score info outside of assembling feature."
            }
        }

        val scoring = VJPair(
            V = datasets.first().assemblerParameters.cloneFactoryParameters.vParameters,
            J = datasets.first().assemblerParameters.cloneFactoryParameters.jParameters
        )

        ValidationException.requireTheSame(datasets.map { it.header.featuresToAlignMap }) {
            "Require the same features to align for all input files"
        }

        val targetFeature = (datasets.map { it.header.allFullyCoveredBy!! }.distinct() + maximumGeneFeature)
            .reduce { acc, next ->
                acc.intersection(next) ?: throw ValidationException("$acc and $next have no intersection")
            }

        val assemblerParameters = assemblerParameters(targetFeature)

        datasets.forEachIndexed { i, reader ->
            outputFiles[i].parent.createDirectories()
            outputFiles[i].deleteIfExists()
            if (!recalculateScores && reader.header.allFullyCoveredBy == targetFeature) {
                logger.progress { "Copying ${inputFiles[i]}" }
                inputFiles[i].copyTo(outputFiles[i])
            } else {
                val cloneFactoryParameters = reader.assemblerParameters.cloneFactoryParameters
                    .setVParameters(scoring.V)
                    .setVParameters(scoring.J)

                val report: CommandReduceToCommonAssembleFeatureReport
                val resultClones = if (reader.header.allFullyCoveredBy == targetFeature) {
                    TODO()
                } else {
                    val cloneFactory = CloneFactory(
                        cloneFactoryParameters,
                        targetFeature.features.toTypedArray(),
                        reader.usedGenes,
                        reader.header.featuresToAlignMap
                    )

                    val reassembleReportBuilder = FullSeqAssemblerReportBuilder()
                    reassembleReportBuilder.setCommandLine(commandLineArguments)
                    reassembleReportBuilder.setInputFiles(inputFiles[i])
                    reassembleReportBuilder.setOutputFiles(outputFiles[i])
                    reassembleReportBuilder.setStartMillis(System.currentTimeMillis())

                    val clonesFilteredOutNoCDR3 = LongAdder()
                    val clonesFilteredOutFeatureIsNotAvailable = LongAdder()

                    // TODO optional recalculation of the score
                    val result = reader.readClones()
                        .reportProgress("Grouping by assemble feature: ${inputFiles[i]}")
                        .filter { clone ->
                            if (targetFeature.features.any { !clone.isAvailable(it) }) {
                                // non-functional clones that don't have a reference point for the target feature
                                clonesFilteredOutFeatureIsNotAvailable.increment()
                                false
                            } else if (!clone.isAvailable(CDR3)) {
                                clonesFilteredOutNoCDR3.increment()
                                false
                            } else
                                true
                        }
                        .groupByOnDisk(
                            ComparatorWithHash.Companion.compareBy { clone ->
                                targetFeature.features.map { clone.getNFeature(it)!! }
                                    .reduce { acc, next -> acc + next }
                            },
                            tempDest,
                            "group_by_assemble_feature",
                            stateBuilder = reader.header.constructStateBuilder(reader.usedGenes)
                        )
                        .reportProgress("Reassembling ${inputFiles[i]}")
                        .map { it.toList() }
                        // also make `it.toList()` synchronized
                        .buffered(threadsOptions.value)
                        .mapInParallel(1) { clones ->
                            val clone = clones.minBy { it.ranks.byReads }.cutByCDR3()
                            val fullSeqAssembler = FullSeqAssembler(
                                cloneFactory, assemblerParameters,
                                arrayOf(CDR3),
                                clone, reader.header.alignerParameters,
                                clone.getBestHit(Variable), clone.getBestHit(Joining)
                            )
                            fullSeqAssembler.report = reassembleReportBuilder
                            val rawVariantsData = fullSeqAssembler.calculateRawData {
                                CUtils.asOutputPort(clones
                                    .flatMap { clone ->
                                        clone.tagCount.tuples().map { tuple ->
                                            VDJCAlignments(
                                                hits = clone.hits,
                                                tagCount = TagCount(tuple),
                                                targets = clone.getTargets(),
                                                history = null,
                                                originalSequences = null
                                            )
                                        }
                                    }
                                )
                            }
                            fullSeqAssembler.callVariants(rawVariantsData)
                        }
                        .toList()
                        .flatMap { it.toList() }
                    report = CommandReduceToCommonAssembleFeatureReport.Reassemble(
                        reassembleReportBuilder.buildReport(),
                        clonesFilteredOutNoCDR3 = clonesFilteredOutNoCDR3.sum(),
                        clonesFilteredOutFeatureIsNotAvailable = clonesFilteredOutFeatureIsNotAvailable.sum()
                    )
                    result
                }
                val resultHeader = reader.header
                    .copy(allFullyCoveredBy = targetFeature)
                    .copy(
                        assemblerParameters = reader.header.assemblerParameters!!
                            .setCloneFactoryParameters(cloneFactoryParameters)
                    )

                val cloneSet = CloneSet.Builder(resultClones, reader.usedGenes, resultHeader)
                    .sort(reader.ordering)
                    .recalculateRanks()
                    .calculateTotalCounts()
                    .build()
                ClnsWriter(outputFiles[i]).use { writer ->
                    writer.writeCloneSet(cloneSet)
                    writer.setFooter(
                        reader.footer.addStepReport(
                            MiXCRCommandDescriptor.reduceToCommonAssembleFeature,
                            report
                        )
                    )
                }
                // Writing report to stout
                ReportUtil.writeReportToStdout(report)
                reportOptions.appendToFiles(report)
            }
        }

        logger.progress {
            "Analysis time: " + FormatUtils.nanoTimeToString(Duration.between(begin, Instant.now()).toNanos())
        }
    }

    private fun Clone.cutByCDR3(): Clone {
        // TODO do it in one go
        val cutHits = EnumMap<GeneType, Array<VDJCHit>>(GeneType::class.java)
        for (geneType in arrayOf(Diversity, Constant)) {
            if (getBestHit(geneType) != null)
                cutHits[geneType] = getHits(geneType)
        }
        for (geneType in GeneType.VJ_REFERENCE) {
            val converted = getHits(geneType).map { hit ->
                hit.mapAlignments { alignment ->
                    if (geneType == Variable) {
                        val from = hit.gene.partitioning.getRelativePosition(hit.alignedFeature, CDR3Begin)
                        if (from !in alignment.sequence1Range) return@mapAlignments null
                        val rangeToKeep = alignment.sequence1Range.setLower(from)
                        val resultMutations = alignment.absoluteMutations.extractAbsoluteMutationsForRange(rangeToKeep)
                        val seq2Range = alignment.sequence2Range.setLower(
                            alignment.sequence2Range.to - rangeToKeep.length() - resultMutations.lengthDelta
                        )
                        Alignment(
                            alignment.sequence1,
                            resultMutations,
                            rangeToKeep,
                            seq2Range,
                            alignment.score
                        )
                    } else {
                        val to = hit.gene.partitioning.getRelativePosition(hit.alignedFeature, CDR3End)
                        if (to !in alignment.sequence1Range) return@mapAlignments null
                        val rangeToKeep = alignment.sequence1Range.setUpper(to)
                        val resultMutations = alignment.absoluteMutations.extractAbsoluteMutationsForRange(rangeToKeep)
                        val seq2Range = alignment.sequence2Range.setUpper(
                            alignment.sequence2Range.from + rangeToKeep.length() + resultMutations.lengthDelta
                        )
                        Alignment(
                            alignment.sequence1,
                            resultMutations,
                            rangeToKeep,
                            seq2Range,
                            alignment.score
                        )
                    }
                }
            }
            cutHits[geneType] = converted.toTypedArray()
        }
        val withCutCDR3 = withHits(cutHits)
        val shift = withCutCDR3.getBestHit(Variable)!!.getAlignment(0)!!.sequence2Range.from
        val shiftedHits = EnumMap<GeneType, Array<VDJCHit>>(GeneType::class.java)
        withCutCDR3.hits.map { (geneType, hits) ->
            shiftedHits[geneType] = hits.map { hit ->
                hit.mapAlignments { alignment ->
                    alignment.move(-shift)
                }
            }.toTypedArray()
        }
        return withCutCDR3
            .withHits(shiftedHits)
            .withTargets(arrayOf(getFeature(CDR3)!!))
    }

    companion object {
        const val COMMAND_NAME = MiXCRCommandDescriptor.reduceToCommonAssembleFeature.name

        private const val inputsLabel = "(input_file.(clns|clna)|directory)..."

        private const val outputLabel = "template.clns"


        fun mkCommandSpec(): CommandSpec =
            CommandSpec.forAnnotatedObject(CommandReduceToCommonAssembleFeature::class.java)
                .addPositional(
                    CommandLine.Model.PositionalParamSpec.builder()
                        .index("0")
                        .required(false)
                        .arity("0..*")
                        .type(Path::class.java)
                        .paramLabel(inputsLabel)
                        .hideParamSyntax(true)
                        .description(
                            "Input files or directory with files for process.",
                            "In case of directory no filter by file type will be applied."
                        )
                        .build()
                )
                .addPositional(
                    CommandLine.Model.PositionalParamSpec.builder()
                        .index("1")
                        .required(false)
                        .arity("0..*")
                        .type(Path::class.java)
                        .paramLabel(outputLabel)
                        .hideParamSyntax(true)
                        .description(OutputTemplate.description)
                        .build()
                )
    }
}
