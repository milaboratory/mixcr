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
package com.milaboratory.mixcr.cli.analyze

import com.milaboratory.mixcr.cli.CommandExtend
import com.milaboratory.mixcr.cli.CommonDescriptions
import io.repseq.core.Chains
import picocli.CommandLine.Option

class CommandAnalyzeShotgun : AnalyzeWithAssemblePartialWithContigs() {
    @Option(
        description = [CommonDescriptions.SPECIES],
        names = ["-s", "--species"]
    )
    var species: String = ""

    var receptorType: Chains.NamedChains = Chains.ALL_NAMED

    @Option(
        names = ["--receptor-type"],
        description = ["Receptor type. Possible values: tcr, bcr, xcr (default), trad, trb, trg, igh, igk, igl"],
    )
    fun setReceptorType(s: String) {
        this.receptorType = Chains.getNamedChains(s)
    }

    private var startingMaterial: StartingMaterial = StartingMaterial.rna

    @Option(
        names = ["--starting-material"],
        description = ["Starting material. Possible values: rna, dna"],
        required = true
    )
    fun setStartingMaterial(s: String) {
        this.startingMaterial = StartingMaterial.valueOf(s)
    }

    @Option(
        names = ["--contig-assembly"],
        description = ["Assemble longest possible sequences from input data. Useful for shotgun-like data."]
    )
    var contigAssembly = false

    /** number of rounds for assemblePartial  */
    @Option(
        names = ["--assemble-partial-rounds"],
        description = ["Number of rounds of assemblePartial"]
    )
    var nAssemblePartialRounds = 0

    /** whether to perform TCR alignments extension  */
    @Option(
        names = ["--do-not-extend-alignments"],
        description = ["Skip TCR alignments extension"]
    )
    var doNotExtendAlignments = false

    @Option(
        names = ["--extend"],
        description = ["Additional parameters for extend step specified with double quotes (e.g --extend \"--chains TRB\" --extend \"--quality 0\" etc."],
        arity = "1"
    )
    var extendOverrides: List<String> = emptyList()
    val extendOps = mutableListOf<String>()

    /** run extend */
    fun runExtend(input: String, output: String): String {
        // reports & commons
        inheritOptions(extendOps)
        // additional parameters
        extendOps += extendOverrides
        // add input output
        extendOps += "$input $output"

        AnalyzeUtil.runCommand(CommandExtend(), spec, extendOps)

        return output
    }

    override fun run0() {
        val geneFeatureToAlign = when (startingMaterial) {
            StartingMaterial.rna -> "VTranscriptWithP"
            StartingMaterial.dna -> "VGeneWithP"
        }

        alignOps += """
            --species $species
            --preset rna-seq
            -OvParameters.geneFeatureToAlign=$geneFeatureToAlign
            -OvParameters.parameters.floatingLeftBound=false
            -OjParameters.parameters.floatingRightBound=false
            -OcParameters.parameters.floatingRightBound=false
            -OallowPartialAlignments=true
            -OallowNoCDR3PartAlignments=true
        """.trimIndent()

        if (contigAssembly)
            assembleOps += "--write-alignments"

        assembleOps += """
            -OassemblingFeatures=CDR3
            -OseparateByV=true
            -OseparateByJ=true
        """.trimIndent()

        var vdjca = runAlign("$prefix.vdjca")
        if (nAssemblePartialRounds > 0)
            for (i in 1..nAssemblePartialRounds)
                vdjca = runAssemblePartial(vdjca, "$prefix.rescued_$i.vdjca")

        if (!doNotExtendAlignments)
            vdjca = runExtend(vdjca, "$prefix.extended.vdjca")

        var clnx = runAssemble(input = vdjca, output = prefix + if (contigAssembly) ".clna" else ".clns")
        if (contigAssembly)
            clnx = runAssembleContigs(input = clnx, output = "$prefix.contigs.clns")

        if (!noExport)
            runExportPerEachChain(receptorType.chains, clnx)
    }

    override fun getInputFiles() = emptyList<String>()
    override fun getOutputFiles() = emptyList<String>()
}