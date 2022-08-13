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
@file:Suppress("EnumEntryName", "ClassName", "PrivatePropertyName")

package com.milaboratory.mixcr.cli.analyze

import com.milaboratory.mixcr.cli.CommonDescriptions
import io.repseq.core.Chains
import io.repseq.core.Chains.ALL_NAMED
import io.repseq.core.Chains.NamedChains
import io.repseq.core.GeneFeature
import picocli.CommandLine.Option

class CommandAnalyzeAmplicon : AnalyzeWithBarcodesWithContigs() {
    @Option(
        description = [CommonDescriptions.SPECIES],
        names = ["-s", "--species"],
        required = true
    )
    var species: String = ""

    private var startingMaterial: StartingMaterial? = null

    @Option(
        names = ["--starting-material"],
        description = ["Starting material. Possible values: rna, dna"],
        required = true
    )
    fun setStartingMaterial(s: String) {
        this.startingMaterial = StartingMaterial.valueOf(s)
    }

    private var _5end: `5End`? = null

    @Option(
        names = ["--5-end"],
        description = ["5'-end of the library. Possible values: no-v-primers, v-primers"],
        required = true
    )
    fun set5End(s: String) {
        this._5end = `5End`.valueOf(s)
    }

    private var _3end: `3End`? = null

    @Option(
        names = ["--3-end"],
        description = ["3'-end of the library. Possible values: j-primers, j-c-intron-primers, c-primers"],
        required = true
    )
    fun set3End(s: String) {
        this._3end = `3End`.valueOf(s)
    }

    private var adapters: Adapters? = null

    @Option(
        names = ["--adapters"],
        description = ["Presence of PCR primers and/or adapter sequences. If sequences of primers used for PCR or adapters are present in sequencing data, it may influence the accuracy of V, J and C gene segments identification and CDR3 mapping. Possible values: adapters-present, no-adapters"],
        required = true
    )
    fun setAdapters(s: String) {
        this.adapters = Adapters.valueOf(s)
    }

    @Option(description = ["UMI pattern to extract from the read."], names = ["--umi-pattern"])
    var umiPattern: String? = null

    @Option(description = ["UMI pattern name from the built-in list."], names = ["--umi-pattern-name"])
    var umiPatternName: String? = null

    @Option(description = ["Read UMI pattern from a file."], names = ["--umi-pattern-file"])
    var umiPatternFile: String? = null

    var receptorType: NamedChains = ALL_NAMED

    @Option(
        names = ["--receptor-type"],
        description = ["Receptor type. Possible values: tcr, bcr, xcr (default), trad, trb, trg, igh, igk, igl"],
    )
    fun setReceptorType(s: String) {
        this.receptorType = Chains.getNamedChains(s)
    }

    private var assemblingFeature = GeneFeature.CDR3

    @Option(
        names = ["--region-of-interest"],
        description = ["MiXCR will use only reads covering the whole target region; reads which partially cover selected region will be dropped during clonotype assembly. All non-CDR3 options require long high-quality paired-end data."],
    )
    private fun setRegionOfInterest(v: String) {
        try {
            assemblingFeature = GeneFeature.parse(v)
        } catch (e: Exception) {
            throwValidationException("Illegal gene feature: $v")
        }
        if (!assemblingFeature.contains(GeneFeature.ShortCDR3))
            throwValidationException("--region-of-interest must cover CDR3")
    }

    @Option(
        description = ["Aligner parameters preset"],
        names = ["--align-preset"]
    )
    var alignPreset: String? = null

    @Option(
        names = ["--contig-assembly"],
        description = ["Assemble longest possible sequences from input data. Useful for shotgun-like data."]
    )
    var contigAssembly = false

    override fun run0() {
        // setup align preset
        if (alignPreset == null) {
            alignPreset = if (receptorType.chains.intersects(Chains.IG))
                "kAligner2"
            else
                "rna-seq"
        }

        // NOTE 5UTR:
        // (1) [ adapters == _Adapters.noAdapters ]
        // If user specified that no adapter sequences are present in the data
        // we can safely extend reference V region to cover 5'UTR, as there is
        // no chance of false alignment extension over non-mRNA derived sequence
        //
        // (2) If [ vPrimers == _5EndPrimers.vPrimers && adapters == _Adapters.adaptersPresent ]
        // VAlignerParameters.floatingLeftBound will be true, so it is also safe to add 5'UTR to the
        // reference as the alignment will not be extended if sequences don't match.
        //
        // In all other cases addition of 5'UTR to the reference may lead to false extension of V alignment
        // over adapter sequence.
        // return adapters == _Adapters.noAdapters || vPrimers == _5EndPrimers.vPrimers; // read as adapters == _Adapters.noAdapters || floatingV()
        val geneFeatureToAlign = when (startingMaterial) {
            StartingMaterial.rna -> "VTranscriptWithP"
            StartingMaterial.dna -> "VGeneWithP"
            null -> throw RuntimeException()
        }

        val floatingV = _5end == `5End`.`v-primers` || adapters == Adapters.`adapters-present`
        val floatingJ = _3end == `3End`.`j-primers` && adapters == Adapters.`adapters-present`
        val floatingC = _3end == `3End`.`c-primers` && adapters == Adapters.`adapters-present`

        val needCorrectAndSortTags = umiPattern != null || umiPatternName != null || umiPatternFile != null

        //--- align

        alignOps += """
            --species $species
            --preset $alignPreset
            -OvParameters.geneFeatureToAlign=$geneFeatureToAlign
            -OvParameters.parameters.floatingLeftBound=$floatingV
            -OjParameters.parameters.floatingRightBound=$floatingJ
            -OcParameters.parameters.floatingRightBound=$floatingC
        """.trimIndent()

        if (umiPattern != null)
            alignOps += "--tag-pattern $umiPattern"
        else if (umiPatternName != null)
            alignOps += "--tag-pattern-name $umiPatternName"
        else if (umiPatternFile != null)
            alignOps += "--tag-pattern-file $umiPatternFile"


        //--- assemble

        if (contigAssembly)
            assembleOps += "--write-alignments"

        assembleOps += """
            -OassemblingFeatures="[${GeneFeature.encode(assemblingFeature).replace(" ".toRegex(), "")}]"
            -OseparateByV=${!floatingV}
            -OseparateByJ=${!floatingJ}
            -OseparateByC=${!(floatingC || floatingJ)} 
        """.trimIndent()

        //--- export

        if (needCorrectAndSortTags)
            exportOps += "-uniqueTagCount UMI"


        ///// running

        var vdjca = runAlign(output = "$prefix.vdjca")
        if (needCorrectAndSortTags) {
            val newVdjca = "$prefix.corrected.vdjca"
            runCorrectAndSortTags(input = vdjca, output = newVdjca)
            vdjca = newVdjca
        }

        var clnx = runAssemble(input = vdjca, output = prefix + if (contigAssembly) ".clna" else ".clns")
        if (contigAssembly)
            clnx = runAssembleContigs(input = clnx, output = "$prefix.contigs.clns")

        if (!noExport)
            runExportPerEachChain(receptorType.chains, clnx)
    }

    override fun getInputFiles() = emptyList<String>()
    override fun getOutputFiles() = emptyList<String>()
}