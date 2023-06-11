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

import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.CloneSet
import com.milaboratory.mixcr.basictypes.CloneSetIO
import com.milaboratory.util.ReportHelper
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Constant
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.VDJCGeneId
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.io.PrintStream
import java.nio.file.Path

@Command(
    description = ["Calculates the difference between two .clns files."]
)
class CommandClonesDiff : MiXCRCommandWithOutputs() {
    @Parameters(paramLabel = "input1.(clns|clna)", index = "0")
    lateinit var in1: Path

    @Parameters(paramLabel = "input2.(clns|clna)", index = "1")
    lateinit var in2: Path

    @Parameters(
        description = ["Path where to write report. Will write to output if omitted."],
        paramLabel = "report.txt",
        index = "2",
        arity = "0..1"
    )
    var report: Path? = null

    @Option(
        names = ["-v", "--use-v"],
        description = ["Use V gene in clone comparison (include it as a clone key along with a clone sequence)."],
        order = OptionsOrder.main + 10_100
    )
    var useV = false

    @Option(
        names = ["-j", "--use-j"],
        description = ["Use J gene in clone comparison (include it as a clone key along with a clone sequence)."],
        order = OptionsOrder.main + 10_200
    )
    var useJ = false

    @Option(
        names = ["-c", "--use-cc"],
        description = ["Use C gene in clone comparison (include it as a clone key along with a clone sequence)."],
        order = OptionsOrder.main + 10_300
    )
    var useC = false

    //@Parameter(names = {"-o1", "--only-in-first"}, description = "output for alignments contained only " +
    //        "in the first .vdjca file")
    //public String onlyFirst;
    //@Parameter(names = {"-o2", "--only-in-second"}, description = "output for alignments contained only " +
    //        "in the second .vdjca file")
    //public String onlySecond;
    //@Parameter(names = {"-d1", "--diff-from-first"}, description = "output for alignments from the first file " +
    //        "that are different from those alignments in the second file")
    //public String diff1;
    //@Parameter(names = {"-d2", "--diff-from-second"}, description = "output for alignments from the second file " +
    //        "that are different from those alignments in the first file")
    //public String diff2;
    //@Parameter(names = {"-g", "--gene-feature"}, description = "Gene feature to compare")
    //public String geneFeatureToMatch = "CDR3";
    //@Parameter(names = {"-l", "--top-hits-level"}, description = "Number of top hits to search for match")
    //public int hitsCompareLevel = 1;
    override val inputFiles
        get() = listOf(in1, in2)

    override val outputFiles
        get() = listOfNotNull(report)

    override fun validate() {
        ValidationException.requireFileType(in1, InputFileType.CLNX)
        ValidationException.requireFileType(in2, InputFileType.CLNX)
        ValidationException.requireFileType(report, InputFileType.TXT)
    }

    override fun run1() {
        (report?.let { PrintStream(it.toFile()) } ?: System.out).use { report ->
            val cs1 = CloneSetIO.read(in1)
            val cs2 = CloneSetIO.read(in2)
            val recs = mutableMapOf<CKey, CRec>()
            populate(recs, cs1, 0)
            populate(recs, cs2, 1)
            var newClones1 = 0
            var newClones2 = 0
            var newClones1Reads: Long = 0
            var newClones2Reads: Long = 0
            for (cRec in recs.values) {
                if (cRec.clones[0] == null) {
                    newClones2++
                    newClones2Reads += cRec.clones[1]!!.count.toLong()
                }
                if (cRec.clones[1] == null) {
                    newClones1++
                    newClones1Reads += cRec.clones[0]!!.count.toLong()
                }
            }
            report.println(
                "Unique clones in cloneset 1: $newClones1 " +
                        "(${ReportHelper.PERCENT_FORMAT.format(100.0 * newClones1 / cs1.size())}%)"
            )
            report.println(
                "Reads in unique clones in cloneset 1: $newClones1Reads " +
                        "(${ReportHelper.PERCENT_FORMAT.format(100.0 * newClones1Reads / cs1.totalCount)}%)"
            )
            report.println(
                "Unique clones in cloneset 2: $newClones2 " +
                        "(${ReportHelper.PERCENT_FORMAT.format(100.0 * newClones2 / cs2.size())}%)"
            )
            report.println(
                "Reads in unique clones in cloneset 2: $newClones2Reads " +
                        "(${ReportHelper.PERCENT_FORMAT.format(100.0 * newClones2Reads / cs2.totalCount)}%)"
            )
        }
    }

    private fun populate(recs: MutableMap<CKey, CRec>, cs: CloneSet, i: Int) {
        for (clone in cs) {
            val key = getKey(clone)
            val cRec = recs.computeIfAbsent(key) { CRec() }
            cRec.clones[i]?.let { unexpectedClone ->
                val letter = when {
                    getBestGene(unexpectedClone, Constant) != getBestGene(clone, Constant) -> 'c'
                    getBestGene(unexpectedClone, Joining) != getBestGene(clone, Joining) -> 'j'
                    getBestGene(unexpectedClone, Variable) != getBestGene(clone, Variable) -> 'v'
                    else -> 'X'
                }
                val error: String = when {
                    letter != 'X' -> "Error: clones with the same key present in one of the clonesets. Seems that clones were assembled " +
                            "using -OseparateBy${letter.uppercaseChar()}=true option, please add -$letter option to this command."
                    else -> ""
                }
                throw ValidationException(error)
            }
            cRec.clones[i] = clone
        }
    }

    private fun getBestGene(clone: Clone, geneType: GeneType): VDJCGeneId? {
        return clone.getBestHit(geneType)?.gene?.id
    }

    private fun getKey(clone: Clone): CKey {
        val clonalSequence = Array<NucleotideSequence>(clone.numberOfTargets()) { i ->
            clone.getTarget(i).sequence
        }
        val v = if (useV) getBestGene(clone, Variable) else null
        val j = if (useJ) getBestGene(clone, Joining) else null
        val c = if (useC) getBestGene(clone, Constant) else null
        return CKey(clonalSequence, v, j, c)
    }

    private class CRec {
        val clones = arrayOfNulls<Clone>(2)
    }

    private class CKey(
        val clonalSequence: Array<NucleotideSequence>,
        val v: VDJCGeneId?,
        val j: VDJCGeneId?,
        val c: VDJCGeneId?
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as CKey

            if (!clonalSequence.contentEquals(other.clonalSequence)) return false
            if (v != other.v) return false
            if (j != other.j) return false
            if (c != other.c) return false

            return true
        }

        override fun hashCode(): Int {
            var result = clonalSequence.contentHashCode()
            result = 31 * result + (v?.hashCode() ?: 0)
            result = 31 * result + (j?.hashCode() ?: 0)
            result = 31 * result + (c?.hashCode() ?: 0)
            return result
        }
    }
}
