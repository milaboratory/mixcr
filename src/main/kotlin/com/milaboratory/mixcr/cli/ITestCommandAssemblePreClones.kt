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

import cc.redberry.pipe.CUtils
import com.milaboratory.app.ApplicationException
import com.milaboratory.app.ValidationException
import com.milaboratory.mixcr.assembler.preclone.FilePreCloneReader
import com.milaboratory.mixcr.assembler.preclone.PreCloneAssemblerParameters
import com.milaboratory.mixcr.assembler.preclone.PreCloneAssemblerRunner
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import com.milaboratory.mixcr.basictypes.tag.TagTuple
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.util.JsonOverrider
import com.milaboratory.util.ReportHelper
import com.milaboratory.util.SmartProgressReporter
import com.milaboratory.util.TempFileManager
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.BufferedOutputStream
import java.io.FileOutputStream
import java.io.PrintStream
import java.nio.file.Path
import java.util.*

@Command(hidden = true)
class ITestCommandAssemblePreClones : MiXCRCommandWithOutputs() {
    @Parameters(arity = "4", description = ["input_file output_file output_clones output_alignments"])
    lateinit var files: List<Path>

    @Option(
        description = ["Overlap sequences on the cell level instead of UMIs for tagged data with molecular and cell barcodes"],
        names = ["--cell-level"]
    )
    var cellLevel = false

    @Option(
        description = ["Use system temp folder for temporary files, the output folder will be used if this option is omitted."],
        names = ["--use-system-temp"]
    )
    var useSystemTemp = false

    @Option(names = ["-P"], description = ["Overrides default pre-clone assembler parameter values."])
    private val preCloneAssemblerOverrides: Map<String, String> = HashMap()

    override val inputFiles
        get() = files.subList(0, 1)

    override val outputFiles
        get() = files.subList(1, 3)

    override fun run1() {
        var params = PreCloneAssemblerParameters.getDefaultParameters(cellLevel)
        if (preCloneAssemblerOverrides.isNotEmpty()) {
            params = JsonOverrider.override(
                params,
                PreCloneAssemblerParameters::class.java,
                preCloneAssemblerOverrides
            )
            if (params == null)
                throw ValidationException("Failed to override some pre-clone assembler parameters: $preCloneAssemblerOverrides")
        }
        var totalAlignments: Long
        val tmp = TempFileManager.smartTempDestination(files[1], "", useSystemTemp)
        var cdr3Hash = 0
        VDJCAlignmentsReader(files[0]).use { alignmentsReader ->
            totalAlignments = alignmentsReader.numberOfAlignments
            val assemblerRunner = PreCloneAssemblerRunner(
                alignmentsReader,
                if (cellLevel) TagType.Cell else TagType.Molecule, arrayOf(GeneFeature.CDR3),
                params,
                files[1],
                tmp
            )
            SmartProgressReporter.startProgressReport(assemblerRunner)
            assemblerRunner.run()
            assemblerRunner.report.buildReport().writeReport(ReportHelper.STDOUT)
            val tagTuples: MutableSet<TagTuple> = HashSet()
            var prevTagKey: TagTuple? = null
            for (al in CUtils.it(alignmentsReader.readAlignments())) {
                cdr3Hash += Objects.hashCode(al.getFeature(GeneFeature.CDR3))
                val tagKey = al.tagCount.asKeyPrefixOrError(alignmentsReader.header.tagsInfo.getSortingLevel())
                if (tagKey != prevTagKey) {
                    if (!tagTuples.add(tagKey)) throw ApplicationException("broken sorting: $tagKey")
                    prevTagKey = tagKey
                }
            }
        }
        FilePreCloneReader(files[1]).use { reader ->
            val totalClones = reader.numberOfClones

            // Checking and exporting alignments
            var numberOfAlignmentsCheck: Long = 0
            PrintStream(BufferedOutputStream(FileOutputStream(files[2].toFile()), 1 shl 20)).use { ps ->
                ps.print("alignmentId\t")
                for (ti in reader.tagsInfo) ps.print(ti.name + "\t")
                ps.println("readIndex\tcloneId\tcdr3\tcdr3_qual\tbestV\tbestJ")
                val aReader = reader.readAlignments()
                SmartProgressReporter.startProgressReport("Exporting alignments", aReader)
                for (al in CUtils.it(aReader)) {
                    cdr3Hash -= Objects.hashCode(al.getFeature(GeneFeature.CDR3))
                    numberOfAlignmentsCheck++
                    val it = al.tagCount.iterator()
                    while (it.hasNext()) {
                        it.advance()
                        ps.print(numberOfAlignmentsCheck.toString() + "\t")
                        for (tv in it.key()) ps.print(tv.toString() + "\t")
                        ps.print(
                            al.minReadId.toString() + "\t" +
                                    al.cloneIndex + "\t"
                        )
                        val cdr3 = al.getFeature(GeneFeature.CDR3)
                        if (cdr3 != null) ps.print(cdr3.sequence)
                        ps.print("\t")
                        if (cdr3 != null) ps.print(cdr3.quality)
                        ps.print("\t")
                        if (al.getBestHit(Variable) != null) ps.print(al.getBestHit(Variable)!!.gene.name)
                        ps.print("\t")
                        if (al.getBestHit(Joining) != null) ps.print(al.getBestHit(Joining)!!.gene.name)
                        ps.println()
                    }
                }
            }
            if (cdr3Hash != 0) throw ApplicationException("inconsistent alignment composition between initial file and pre-clone container")
            if (numberOfAlignmentsCheck != totalAlignments) {
                throw ApplicationException(
                    "numberOfAlignmentsCheck != totalAlignments ($numberOfAlignmentsCheck != $totalAlignments)"
                )
            }
            for (al in CUtils.it(reader.readAssignedAlignments())) numberOfAlignmentsCheck--
            for (al in CUtils.it(reader.readUnassignedAlignments())) numberOfAlignmentsCheck--
            if (numberOfAlignmentsCheck != 0L) throw ApplicationException(
                "numberOfAlignmentsCheck != 0 ($numberOfAlignmentsCheck != 0)"
            )

            // Checking and exporting clones
            var numberOfClonesCheck: Long = 0
            PrintStream(BufferedOutputStream(FileOutputStream(files[3].toFile()), 1 shl 20)).use { ps ->
                ps.print("cloneId\t")
                for (ti in reader.tagsInfo) ps.print(ti.name + "\t")
                ps.println("count\tcount_full\tcdr3\tbestV\tbestJ")
                val cReader = reader.readPreClones()
                SmartProgressReporter.startProgressReport("Exporting clones", cReader)
                for (c in CUtils.it(cReader)) {
                    val it = c.coreTagCount.iterator()
                    while (it.hasNext()) {
                        it.advance()
                        // if (!tagTuples.add(it.key()))
                        //     throwExecutionException("duplicate clone tag tuple: " + it.key());
                        ps.print(c.index.toString() + "\t")
                        for (tv in it.key()) ps.print(tv.toString() + "\t")
                        ps.print(it.value().toString() + "\t")
                        ps.print(c.fullTagCount[it.key()].toString() + "\t")
                        ps.println(
                            c.clonalSequence[0].sequence.toString() + "\t" +
                                    c.getBestGene(Variable).name + "\t" +
                                    c.getBestGene(Joining).name
                        )
                    }
                    numberOfClonesCheck++
                }
            }
            if (numberOfClonesCheck != totalClones) throw ApplicationException(
                "numberOfClonesCheck != totalClones ($numberOfClonesCheck != $totalClones)"
            )
        }
    }
}
