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

import cc.redberry.pipe.OutputPort
import cc.redberry.pipe.util.forEach
import cc.redberry.pipe.util.withCounting
import com.milaboratory.mixcr.basictypes.HasFeatureToAlign
import com.milaboratory.mixcr.basictypes.IOUtil
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsReader
import com.milaboratory.mixcr.basictypes.VDJCAlignmentsWriter
import com.milaboratory.primitivio.PipeReader
import com.milaboratory.primitivio.PipeWriter
import com.milaboratory.primitivio.sortOnDisk
import com.milaboratory.util.ObjectSerializer
import com.milaboratory.util.SmartProgressReporter
import io.repseq.core.VDJCGene
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Path

@Command(
    description = ["Sort alignments in vdjca file by read id."]
)
class CommandSortAlignments : MiXCRCommandWithOutputs() {
    @Parameters(paramLabel = "alignments.vdjca", index = "0")
    lateinit var input: Path

    @Parameters(paramLabel = "alignments.sorted.vdjca", index = "1")
    lateinit var out: Path

    override val inputFiles
        get() = listOf(input)

    override val outputFiles
        get() = listOf(out)

    override fun validate() {
        ValidationException.requireFileType(input, InputFileType.VDJCA)
        ValidationException.requireFileType(out, InputFileType.VDJCA)
    }

    override fun run0() {
        VDJCAlignmentsReader(input).use { reader ->
            SmartProgressReporter.startProgressReport("Reading vdjca", reader)
            reader.sortOnDisk(
                Comparator.comparing { it.minReadId },
                VDJCAlignmentsSerializer(reader)
            ) { sorted ->
                VDJCAlignmentsWriter(out).use { writer ->
                    writer.writeHeader(
                        reader.header.updateTagInfo { tagsInfo -> tagsInfo.setSorted(0) },
                        reader.usedGenes
                    )
                    val counter = sorted.withCounting()
                    SmartProgressReporter.startProgressReport(
                        "Writing sorted alignments",
                        SmartProgressReporter.extractProgress(counter, reader.numberOfReads)
                    )
                    counter.forEach { res ->
                        writer.write(res)
                    }
                    writer.setNumberOfProcessedReads(reader.numberOfReads)
                    writer.setFooter(reader.footer)
                }
            }
        }
    }

    private class VDJCAlignmentsSerializer(reader: VDJCAlignmentsReader) : ObjectSerializer<VDJCAlignments> {
        private val featuresToAlign: HasFeatureToAlign = reader.header.featuresToAlign
        private val usedAlleles: List<VDJCGene> = reader.usedGenes

        override fun write(data: Collection<VDJCAlignments>, stream: OutputStream) {
            object : PipeWriter<VDJCAlignments>(stream) {
                override fun init() {
                    IOUtil.stdVDJCPrimitivOStateInit(output, usedAlleles, featuresToAlign)
                }
            }.use { out ->
                data.forEach { datum ->
                    out.put(datum)
                }
            }
        }

        override fun read(stream: InputStream): OutputPort<VDJCAlignments> =
            object : PipeReader<VDJCAlignments>(VDJCAlignments::class.java, stream) {
                override fun init() {
                    IOUtil.stdVDJCPrimitivIStateInit(input, featuresToAlign, usedAlleles[0].parentLibrary.parent)
                }
            }
    }
}
