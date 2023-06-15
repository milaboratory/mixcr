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
import com.milaboratory.app.logger
import io.repseq.core.Chains
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Constant
import io.repseq.core.GeneType.Diversity
import io.repseq.core.GeneType.Joining
import io.repseq.core.GeneType.Variable
import io.repseq.core.VDJCLibraryRegistry
import io.repseq.dto.VDJCDataUtils.writeToFile
import picocli.CommandLine.ArgGroup
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import picocli.CommandLine.Parameters
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

@Command(
    description = ["Build custom reference library"]
)
class CommandBuildLibrary : MiXCRCommandWithOutputs() {

    interface GenesFasta {
        var pathOps: Path?
        var geneFeatureOps: String?

        // path to fasta
        val path: Path get() = pathOps!!

        // gene feature
        val geneFeature: GeneFeature get() = GeneFeature.parse(geneFeatureOps)
    }

    interface GenesInput {
        // either fasta
        val fasta: GenesFasta?

        // or species
        val species: String?
    }

    @ArgGroup(exclusive = true, multiplicity = "1")
    lateinit var vGenes: VGenesInput

    class VGenesInput : GenesInput {
        @ArgGroup(exclusive = false)
        override var fasta: VGenesFasta? = null

        class VGenesFasta : GenesFasta {
            @set:Option(
                names = ["--v-genes-from-fasta"],
                description = ["FASTA file with Variable genes"],
                paramLabel = "<v.fasta>",
                required = true
            )
            override var pathOps: Path? = null
                set(value) {
                    ValidationException.requireFileType(value, InputFileType.FASTA)
                    field = value
                }

            @set:Option(
                names = ["--v-gene-feature"],
                description = ["Gene feature corresponding to Variable gene sequences in FASTA (e.g. VRegion or VGene)"],
                paramLabel = "<gene_feature>",
                required = true
            )
            override var geneFeatureOps: String? = null
                set(value) {
                    ValidationException.requireKnownGeneFeature(value)
                    field = value
                }
        }

        @set:Option(
            names = ["--v-genes-from-species"],
            description = ["Species to take Variable genes from it (human, mmu, lamaGlama, alpaca, rat, spalax"],
            paramLabel = "<species>",
            required = true
        )
        override var species: String? = null
            set(value) {
                ValidationException.requireKnownSpecies(value)
                field = value
            }
    }

    @ArgGroup(exclusive = true, multiplicity = "1")
    lateinit var jGenes: JGenesInput

    class JGenesInput : GenesInput {
        @ArgGroup(exclusive = false)
        override var fasta: JGenesFasta? = null

        class JGenesFasta : GenesFasta {
            @set:Option(
                names = ["--j-genes-from-fasta"],
                description = ["FASTA file with Joining genes"],
                paramLabel = "<j.fasta>",
                required = true
            )
            override var pathOps: Path? = null
                set(value) {
                    ValidationException.requireFileType(value, InputFileType.FASTA)
                    field = value
                }

            @set:Option(
                names = ["--j-gene-feature"],
                description = ["Gene feature corresponding to Joining gene sequences in FASTA (JRegion by default)"],
                paramLabel = "<gene_feature>",
                required = false
            )
            override var geneFeatureOps: String? = "JRegion"
                set(value) {
                    ValidationException.requireKnownGeneFeature(value)
                    field = value
                }
        }

        @set:Option(
            names = ["--j-genes-from-species"],
            description = ["Species to take Joining genes from it (human, mmu, lamaGlama, alpaca, rat, spalax"],
            paramLabel = "<species>",
            required = true
        )
        override var species: String? = null
            set(value) {
                ValidationException.requireKnownSpecies(value)
                field = value
            }
    }

    @ArgGroup(exclusive = true, multiplicity = "0..1")
    var dGenes: DGenesInput? = null

    class DGenesInput : GenesInput {
        @ArgGroup(exclusive = false)
        override var fasta: DGenesFasta? = null

        class DGenesFasta : GenesFasta {
            @set:Option(
                names = ["--d-genes-from-fasta"],
                description = ["FASTA file with Diversity genes"],
                paramLabel = "<j.fasta>",
                required = true
            )
            override var pathOps: Path? = null
                set(value) {
                    ValidationException.requireFileType(value, InputFileType.FASTA)
                    field = value
                }

            @set:Option(
                names = ["--d-gene-feature"],
                description = ["Gene feature corresponding to Diversity gene sequences in FASTA (DRegion by default)"],
                paramLabel = "<gene_feature>",
                required = false
            )
            override var geneFeatureOps: String? = "DRegion"
                set(value) {
                    ValidationException.requireKnownGeneFeature(value)
                    field = value
                }
        }

        @set:Option(
            names = ["--d-genes-from-species"],
            description = ["Species to take Diversity genes from it (human, mmu, lamaGlama, alpaca, rat, spalax"],
            paramLabel = "<species>",
            required = true
        )
        override var species: String? = null
            set(value) {
                ValidationException.requireKnownSpecies(value)
                field = value
            }
    }

    @ArgGroup(exclusive = true, multiplicity = "0..1")
    var cGenes: CGenesInput? = null

    class CGenesInput : GenesInput {
        @ArgGroup(exclusive = false)
        override var fasta: CGenesFasta? = null

        class CGenesFasta : GenesFasta {
            @set:Option(
                names = ["--c-genes-from-fasta"],
                description = ["FASTA file with Constant genes"],
                paramLabel = "<j.fasta>",
                required = true
            )
            override var pathOps: Path? = null
                set(value) {
                    ValidationException.requireFileType(value, InputFileType.FASTA)
                    field = value
                }

            @set:Option(
                names = ["--c-gene-feature"],
                description = ["Gene feature corresponding to Constant gene sequences in FASTA (CExon1 by default)"],
                paramLabel = "<gene_feature>",
                required = false
            )
            override var geneFeatureOps: String? = "CExon1"
                set(value) {
                    ValidationException.requireKnownGeneFeature(value)
                    field = value
                }
        }

        @set:Option(
            names = ["--c-genes-from-species"],
            description = ["Species to take Constant genes from it (human, mmu, lamaGlama, alpaca, rat, spalax"],
            paramLabel = "<species>",
            required = true
        )
        override var species: String? = null
            set(value) {
                ValidationException.requireKnownSpecies(value)
                field = value
            }
    }

    @set:Option(
        names = ["--species"],
        description = ["Species names"],
        paramLabel = "<species name>",
        required = true,

        )
    var species: String = ""

    @set:Option(
        names = ["--taxon-id"],
        description = ["Taxon ID"],
        paramLabel = "<taxon_id>",
    )
    var taxonId: String = "0"

    @set:Option(
        names = ["--chain"],
        description = ["Immunological chain"],
        paramLabel = "<chain>",
        required = true
    )
    var chain: String = ""
        set(value) {
            ValidationException.requireKnownChains(value)
            field = value
        }

    @set:Option(
        names = ["--do-not-infer-points"],
        description = ["Do not infer reference points"],
        required = false
    )
    var doNotInferPoints: Boolean = false

    @set:Option(
        names = ["--keep-intermediate"],
        description = ["Keep intermediate files"],
        required = false
    )
    var keepIntermediateFiles: Boolean = false

    @Parameters(
        description = ["Output library."],
        paramLabel = "library.json",
        index = "0",
    )
    var output: Path? = null
        set(value) {
            ValidationException.requireFileType(value, InputFileType.JSON, InputFileType.JSON_GZ)
        }

    override val inputFiles: List<Path> get() = emptyList()
    override val outputFiles: List<Path> get() = listOfNotNull(output)

    private fun mkLibraryForGeneType(gt: GeneType) = run {
        val input = when (gt) {
            Variable -> vGenes
            Diversity -> dGenes
            Joining -> jGenes
            Constant -> cGenes
        }
        if (input == null) {
            null
        } else if (input.species != null) {
            fromKnownSpecies(gt, input.species!!)
        } else {
            val fasta = input.fasta!!
            fromFasta(gt, fasta.geneFeatureOps!!, fasta.path)
        }
    }

    private fun tmpLibraryName(geneType: GeneType) =
        output!!.absolutePathString().replace(".json", ".${geneType.letter}.json")

    private fun fromKnownSpecies(geneType: GeneType, species: String) = run {
        val chains = Chains.parse(chain)
        val data = VDJCLibraryRegistry.getDefaultLibrary(species).data
        val dataFiltered =
            data.copy(
                genes = data.genes.filter { it.geneType == geneType && it.chains.intersects(chains) },
                speciesNames = listOf(this.species),
                taxonId = java.lang.Long.parseLong(taxonId)
            )
        val libName = tmpLibraryName(geneType)
        writeToFile(listOf(dataFiltered), libName, false)
        libName
    }

    private fun logCmd(cmd: List<String>) {
        logger.debug { "Running:\nrepseqio ${cmd.joinToString(" ")}" }
    }

    private fun fromFasta(
        geneType: GeneType,
        geneFeature: String,
        fasta: Path
    ) = run {
        val libName = tmpLibraryName(geneType)

        val ffCmd = """
            fromFasta
            --taxon-id $taxonId
            --species-name $species
            --gene-type ${geneType.letter}
            --chain $chain
            --gene-feature $geneFeature
            --name-index 0
            -f
            ${fasta.absolutePathString()}
            $libName
        """.trimIndent()
            .replace("\n", " ")
            .split(" ")
            .filter { it.isNotBlank() }
        logCmd(ffCmd)
        io.repseq.cli.Main.main(ffCmd.toTypedArray())

        if (!doNotInferPoints) {
            val ipCmd = listOf("inferPoints", "-g", geneFeature, "-f", libName, libName)
            logCmd(ipCmd)
            io.repseq.cli.Main.main(ipCmd.toTypedArray())
        }

        val coCmd = listOf("compile", "--do-not-compress", "-f", libName, libName)
        logCmd(coCmd)
        io.repseq.cli.Main.main(coCmd.toTypedArray())

        libName
    }

    override fun run1() {
        val libName = output!!.absolutePathString()
        val meCmd = mutableListOf("merge", "-f")
        meCmd += GeneType.values().mapNotNull { mkLibraryForGeneType(it) }
        meCmd += libName
        if (output?.endsWith("gz") == true)
            meCmd += "--compress"
        logCmd(meCmd)
        io.repseq.cli.Main.main(meCmd.toTypedArray())

        if (logger.verbose) {
            val deCmd = listOf("debug", libName)
            io.repseq.cli.Main.main(deCmd.toTypedArray())
        }

        if (!keepIntermediateFiles)
            for (gt in GeneType.values()) {
                Files.deleteIfExists(Paths.get(tmpLibraryName(gt)))
            }
    }
}