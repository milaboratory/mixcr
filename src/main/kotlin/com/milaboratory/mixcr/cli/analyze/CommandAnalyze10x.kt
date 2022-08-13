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

import com.milaboratory.mixcr.cli.CommonDescriptions
import io.repseq.core.Chains
import picocli.CommandLine.Option

class CommandAnalyze10x : AnalyzeWithBarcodesWithAssemlePartialWithContigs() {
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

    @Option(
        description = ["Aligner parameters preset"],
        names = ["--align-preset"]
    )
    var alignPreset: String? = null

    override fun run0() {
        // setup align preset
        if (alignPreset == null) {
            alignPreset = if (receptorType.chains.intersects(Chains.IG))
                "kAligner2"
            else
                "rna-seq"
        }

        alignOps += """
            --species $species
            --preset $alignPreset
            --tag-pattern-name 10x
            -OvParameters.geneFeatureToAlign=VTranscriptWithP
            -OvParameters.parameters.floatingLeftBound=false
            -OjParameters.parameters.floatingRightBound=false
            -OcParameters.parameters.floatingRightBound=false
            -OallowPartialAlignments=true
            -OallowNoCDR3PartAlignments=true
        """.trimIndent()

        assembleOps += """
            --write-alignments
            --separateByV=false
            --separateByJ=false
            --separateByC=true
        """.trimIndent()

        exportOps += """
            --split-by-tag CELL
            -tag CELL
            -uniqueTagCount UMI
        """.trimIndent()

        val vdjca = runAlign("$prefix.vdjca")
        val corrected = runCorrectAndSortTags(vdjca, "$prefix.corrected.vdjca")
        val assembled = runAssemblePartial(corrected, "$prefix.assembled.vdjca")
        val clnx = runAssemble(input = assembled, output = "$prefix.clna")
        val clns = runAssembleContigs(input = clnx, output = "$prefix.contigs.clns")

        if (!noExport)
            runExport(emptyList(), clns, "$prefix.clonotypes.tsv")
    }

    override fun getInputFiles() = emptyList<String>()
    override fun getOutputFiles() = emptyList<String>()
}