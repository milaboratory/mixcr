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
import com.milaboratory.core.alignment.Alignment
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters
import com.milaboratory.mixcr.assembler.CloneFactory
import com.milaboratory.mixcr.assembler.fullseq.CoverageAccumulator
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssembler
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssemblerParameters
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssemblerReportBuilder
import com.milaboratory.mixcr.basictypes.ClnAReader
import com.milaboratory.mixcr.basictypes.ClnAReader.CloneAlignments
import com.milaboratory.mixcr.basictypes.ClnsWriter
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.CloneReader
import com.milaboratory.mixcr.basictypes.CloneSet
import com.milaboratory.mixcr.basictypes.GeneAndScore
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.MiXCRMetaInfo
import com.milaboratory.mixcr.basictypes.VDJCHit
import com.milaboratory.mixcr.basictypes.VDJCSProperties.CloneOrdering
import com.milaboratory.mixcr.basictypes.tag.TagCount
import com.milaboratory.mixcr.basictypes.tag.TagTuple
import com.milaboratory.mixcr.util.Concurrency
import com.milaboratory.mixcr.util.VJPair
import com.milaboratory.mixcr.util.alignmentsCover
import com.milaboratory.primitivio.PipeDataInputReader
import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.forEach
import com.milaboratory.primitivio.mapInParallel
import com.milaboratory.util.JsonOverrider
import com.milaboratory.util.ReportUtil
import com.milaboratory.util.SmartProgressReporter
import com.milaboratory.util.StreamUtil
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Diversity
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.VDJC_REFERENCE
import io.repseq.core.GeneType.Variable
import io.repseq.core.ReferencePoint.CDR3Begin
import io.repseq.core.ReferencePoint.CDR3End
import io.repseq.core.ReferencePoint.FR4End
import io.repseq.core.ReferencePoint.UTR5Begin
import io.repseq.core.VDJCGene
import io.repseq.core.VDJCGeneId
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedWriter
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.*
import java.util.stream.Collectors

@CommandLine.Command(
    name = CommandAssembleContigs.ASSEMBLE_CONTIGS_COMMAND_NAME,
    sortOptions = true,
    separator = " ",
    description = ["Assemble full sequences."]
)
class CommandAssembleContigs : MiXCRCommand() {
    @CommandLine.Parameters(description = ["clones.clna"], index = "0")
    lateinit var `in`: String

    @CommandLine.Parameters(description = ["clones.clns"], index = "1")
    lateinit var out: String

    @CommandLine.Option(description = ["Processing threads"], names = ["-t", "--threads"])
    var threads = Runtime.getRuntime().availableProcessors()
        set(value) {
            if (value <= 0) throwValidationException("-t / --threads must be positive")
            field = value
        }

    @CommandLine.Option(names = ["-O"], description = ["Overrides default parameter values."])
    var overrides: Map<String, String> = HashMap()

    @CommandLine.Option(description = [CommonDescriptions.REPORT], names = ["-r", "--report"])
    var reportFile: String? = null

    @CommandLine.Option(description = ["Ignore tags (UMIs, cell-barcodes)"], names = ["--ignore-tags"])
    var ignoreTags = false

    @CommandLine.Option(description = ["Report file."], names = ["--debug-report"], hidden = true)
    var debugReportFile: String? = null

    @CommandLine.Option(
        description = ["Filter out clones that not covered by the feature. All clones alignments will be cut by this feature"],
        names = [CUT_BY_FEATURE_OPTION_NAME]
    )
    var cutByFeatureParam: String? = null

    private val cutByFeature: GeneFeature? by lazy {
        when {
            cutByFeatureParam != null -> GeneFeature.parse(cutByFeatureParam)
            else -> null
        }
    }

    @CommandLine.Option(description = [CommonDescriptions.JSON_REPORT], names = ["-j", "--json-report"])
    var jsonReport: String? = null

    override fun getInputFiles() = listOf(`in`)

    override fun getOutputFiles() = listOf(out)

    // Perform parameters overriding
    private val fullSeqAssemblerParameters: FullSeqAssemblerParameters
        get() {
            var p = FullSeqAssemblerParameters.getByName("default")
            if (!overrides.isEmpty()) {
                // Perform parameters overriding
                p = JsonOverrider.override(p, FullSeqAssemblerParameters::class.java, overrides)
                if (p == null) throwValidationException("failed to override some parameter: $overrides")
            }
            return p
        }

    private val reportBuilder = FullSeqAssemblerReportBuilder()

    override fun run0() {
        val beginTimestamp = System.currentTimeMillis()
        val assemblerParameters = fullSeqAssemblerParameters
        var totalClonesCount = 0
        val genes: List<VDJCGene>
        val info: MiXCRMetaInfo
        val cloneAssemblerParameters: CloneAssemblerParameters
        val ordering: CloneOrdering
        val reports: List<MiXCRCommandReport>
        ClnAReader(`in`, VDJCLibraryRegistry.getDefault(), Concurrency.noMoreThan(4)).use { reader ->
            PrimitivO(BufferedOutputStream(FileOutputStream(out))).use { tmpOut ->
                debugReportFile?.let { BufferedWriter(OutputStreamWriter(FileOutputStream(it))) }.use { debugReport ->
                    reports = reader.reports()
                    ordering = reader.ordering()
                    val cloneFactory = CloneFactory(
                        reader.assemblerParameters.cloneFactoryParameters,
                        reader.assemblingFeatures, reader.usedGenes, reader.alignerParameters.featuresToAlignMap
                    )
                    info = reader.info
                    cloneAssemblerParameters = reader.assemblerParameters
                    genes = reader.usedGenes

                    IOUtil.registerGeneReferences(tmpOut, genes, info.alignerParameters)
                    val cloneAlignmentsPort = reader.clonesAndAlignments()
                    SmartProgressReporter.startProgressReport("Assembling contigs", cloneAlignmentsPort)
                    val parallelProcessor = cloneAlignmentsPort.mapInParallel(
                        threads,
                        buffer = 1024
                    ) { cloneAlignments: CloneAlignments ->
                        val clone = when {
                            ignoreTags -> cloneAlignments.clone
                                .setTagCount(TagCount(TagTuple.NO_TAGS, cloneAlignments.clone.tagCount.sum()))
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
                                                                cloneAssemblerParameters.assemblingFeatures[0]
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
                                return@mapInParallel arrayOf(clone)
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
                                cloneFactory, assemblerParameters,
                                clone, info.alignerParameters,
                                bestGenes[Variable], bestGenes[Joining]
                            )
                            fullSeqAssembler.report = reportBuilder
                            val rawVariantsData = fullSeqAssembler.calculateRawData { cloneAlignments.alignments() }
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

                            return@mapInParallel fullSeqAssembler.callVariants(rawVariantsData)
                                .cutByGeneFeature(reader)
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
        PrimitivI(BufferedInputStream(FileInputStream(out))).use { tmpIn ->
            IOUtil.registerGeneReferences(tmpIn, genes, info.alignerParameters)
            var i = 0
            PipeDataInputReader(Clone::class.java, tmpIn, totalClonesCount.toLong()).forEach { clone ->
                clones[i++] = clone.setId(cloneId++)
            }
        }
        val cloneSet = CloneSet(listOf(*clones), genes, info.copy(allClonesCutBy = cutByFeature), ordering)
        ClnsWriter(out).use { writer ->
            writer.writeCloneSet(cloneSet)
            reportBuilder.setStartMillis(beginTimestamp)
            reportBuilder.setInputFiles(`in`)
            reportBuilder.setOutputFiles(out)
            reportBuilder.commandLine = commandLineArguments
            reportBuilder.setFinishMillis(System.currentTimeMillis())
            val report = reportBuilder.buildReport()
            // Writing report to stout
            ReportUtil.writeReportToStdout(report)
            if (reportFile != null) ReportUtil.appendReport(reportFile, report)
            if (jsonReport != null) ReportUtil.appendJsonReport(jsonReport, report)
            writer.writeFooter(reports, report)
        }
    }

    private fun Array<Clone>.cutByGeneFeature(reader: CloneReader): Array<Clone> {
        val cutByFeature = cutByFeature ?: return this
        val assemblingFeatures = cutByFeature
            .map { GeneFeature(it.begin, it.end) }
            .toTypedArray()
        val cloneFactory = CloneFactory(
            reader.assemblerParameters.cloneFactoryParameters,
            assemblingFeatures,
            reader.usedGenes,
            reader.alignerParameters.featuresToAlignMap
        )

        val geneFeatureToMatch = VJPair(
            V = GeneFeature.intersection(cutByFeature, GeneFeature(UTR5Begin, CDR3Begin)),
            J = GeneFeature.intersection(cutByFeature, GeneFeature(CDR3End, FR4End))
        )
        return mapNotNull { clone ->
            clone.removeTargetsNotCoveredBy(geneFeatureToMatch)
        }
            .map { clone ->
                val cutTargets = assemblingFeatures
                    .map { clone.getFeature(it) }
                    .toTypedArray()
                val geneScores = EnumMap<GeneType, List<GeneAndScore>>(GeneType::class.java)
                for (gt in VDJC_REFERENCE) {
                    geneScores[gt] = clone.getHits(gt).map { hit -> hit.geneAndScore }
                }

                cloneFactory.create(
                    clone.id,
                    clone.count,
                    geneScores,
                    clone.tagCount,
                    cutTargets,
                    clone.group
                )
            }
            .toTypedArray()
    }

    private fun Clone.removeTargetsNotCoveredBy(geneFeatureToMatch: VJPair<GeneFeature>): Clone? {
        var VJHits = VJPair(
            getHits(Variable),
            getHits(Joining)
        ).map { hits ->
            hits.filter { hit -> hit.alignmentsCover(geneFeatureToMatch) }
        }
        if (VJHits.V.isEmpty() || VJHits.J.isEmpty()) return null
        VJHits = VJHits.map { hits -> hits.removeAlignmentsNotCoveredBy(geneFeatureToMatch) }

        val targetIndexesWithAlignments = (VJHits.V + VJHits.J + (getHits(Diversity) ?: emptyArray()))
            .filterNotNull()
            .flatMap { hit ->
                (0 until hit.numberOfTargets())
                    .filter { i -> hit.getAlignment(i) != null }
            }
            .distinct()
            .sorted()

        VJHits = VJHits.map { hits ->
            hits.map { hit -> hit.filterAlignmentsByTargetIndexes(targetIndexesWithAlignments) }
        }
        val resultHits = EnumMap(hits)
        resultHits[Variable] = VJHits.V.toTypedArray()
        resultHits[Joining] = VJHits.J.toTypedArray()
        return Clone(
            targetIndexesWithAlignments.map { getTarget(it) }.toTypedArray(),
            resultHits,
            tagCount,
            count,
            id,
            group
        )
    }

    private fun List<VDJCHit>.removeAlignmentsNotCoveredBy(geneFeatureToMatch: VJPair<GeneFeature>) =
        filter { it.alignmentsCover(geneFeatureToMatch) }
            .map { hit ->
                var result = hit
                val partitioning = hit.gene.partitioning.getRelativeReferencePoints(hit.alignedFeature)
                val rangesToMatch = partitioning.getRanges(geneFeatureToMatch[hit.geneType])

                for (i in 0 until hit.numberOfTargets()) {
                    val alignment: Alignment<NucleotideSequence>? = hit.getAlignment(i)
                    if (alignment != null && rangesToMatch.none { toMatch -> toMatch in alignment.sequence1Range }) {
                        result = hit.setAlignment(i, null)
                    }
                }
                result
            }

    private fun VDJCHit.filterAlignmentsByTargetIndexes(targetIndexesWithAlignments: List<Int>): VDJCHit {
        val filteredAlignments: Array<Alignment<NucleotideSequence>?> =
            Array(targetIndexesWithAlignments.size) { i ->
                getAlignment(targetIndexesWithAlignments[i])
            }
        return VDJCHit(gene, filteredAlignments, alignedFeature)
    }


    companion object {
        const val ASSEMBLE_CONTIGS_COMMAND_NAME = "assembleContigs"
        const val CUT_BY_FEATURE_OPTION_NAME = "--cut-by-feature"
    }
}
