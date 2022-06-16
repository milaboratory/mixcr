package com.milaboratory.mixcr.cli

import cc.redberry.pipe.CUtils
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.cli.CommandExport.FieldData
import com.milaboratory.mixcr.cli.CommandExport.extractor
import com.milaboratory.mixcr.export.FieldExtractor
import com.milaboratory.mixcr.export.InfoWriter
import com.milaboratory.mixcr.export.OutputMode
import com.milaboratory.mixcr.export.SHMTreeNodeToPrint
import com.milaboratory.mixcr.export.SHNTreeNodeFieldsExtractor
import com.milaboratory.mixcr.trees.SHMTreesReader
import io.repseq.core.VDJCLibraryRegistry
import picocli.CommandLine

@CommandLine.Command(
    name = CommandExportTableForSHMTrees.COMMAND_NAME,
    sortOptions = false,
    separator = " ",
    description = ["Export SHMTree as a table"]
)
class CommandExportTableForSHMTrees : ACommandWithOutputMiXCR() {
    @CommandLine.Parameters(arity = "2", description = ["input_file.tree output_file.tcv"])
    var inOut: List<String> = ArrayList()

    @CommandLine.Option(description = ["Output column headers with spaces."], names = ["-v", "--with-spaces"])
    var humanReadable = false

    override fun getInputFiles(): List<String> = listOf(inOut.first())

    override fun getOutputFiles(): List<String> = listOf(inOut.last())

    override fun run0() {
        InfoWriter<SHMTreeNodeToPrint>(outputFiles.first()).use { output ->

            val oMode = when {
                humanReadable -> OutputMode.HumanFriendly
                else -> OutputMode.ScriptingFriendly
            }

            //copy from com/milaboratory/mixcr/cli/CommandExport.java:446
            val cloneExtractors = listOf(
                FieldData.mk("-cloneId"),
                FieldData.mk("-count"),
                //TODO why NaN
//                FieldData.mk("-fraction"),
                FieldData.mk("-targetSequences"),
                FieldData.mk("-targetQualities"),
                FieldData.mk("-vHitsWithScore"),
                FieldData.mk("-dHitsWithScore"),
                FieldData.mk("-jHitsWithScore"),
                FieldData.mk("-cHitsWithScore"),
                FieldData.mk("-vAlignments"),
                FieldData.mk("-dAlignments"),
                FieldData.mk("-jAlignments"),
                FieldData.mk("-cAlignments"),
                FieldData.mk("-minFeatureQuality", "FR1"),
                FieldData.mk("-minFeatureQuality", "CDR1"),
                FieldData.mk("-minFeatureQuality", "FR2"),
                FieldData.mk("-minFeatureQuality", "CDR2"),
                FieldData.mk("-minFeatureQuality", "FR3"),
                FieldData.mk("-minFeatureQuality", "CDR3"),
                FieldData.mk("-minFeatureQuality", "FR4"),
                FieldData.mk("-defaultAnchorPoints")
            )
                .flatMap { extractor(it, Clone::class.java, oMode) }
                .map { fieldExtractor ->
                    object : FieldExtractor<SHMTreeNodeToPrint> {
                        override fun getHeader() = fieldExtractor.header

                        override fun extractValue(`object`: SHMTreeNodeToPrint): String {
                            val clone = `object`.cloneOrFoundAncestor.clone ?: return ""
                            return fieldExtractor.extractValue(clone)
                        }
                    }
                }
            output.attachInfoProviders(cloneExtractors)


            val nodeExtractors = listOf(
                FieldData.mk("-nFeature", "FR1"),
                FieldData.mk("-nFeature", "CDR1"),
                FieldData.mk("-nFeature", "FR2"),
                FieldData.mk("-nFeature", "CDR2"),
                FieldData.mk("-nFeature", "FR3"),
                FieldData.mk("-nFeature", "CDR3"),
                FieldData.mk("-nFeature", "FR4"),
                FieldData.mk("-aaFeature", "FR1"),
                FieldData.mk("-aaFeature", "CDR1"),
                FieldData.mk("-aaFeature", "FR2"),
                FieldData.mk("-aaFeature", "CDR2"),
                FieldData.mk("-aaFeature", "FR3"),
                FieldData.mk("-aaFeature", "CDR3"),
                FieldData.mk("-aaFeature", "FR4"),
            ).flatMap { SHNTreeNodeFieldsExtractor.extract(it, SHMTreeNodeToPrint::class.java, oMode) }

            output.attachInfoProviders(nodeExtractors)

            output.ensureHeader()

            val libraryRegistry = VDJCLibraryRegistry.getDefault()
            SHMTreesReader(inputFiles.first(), libraryRegistry).use { reader ->
                CUtils.it(reader.readTrees()).forEach { shmTree ->
                    shmTree.tree.allNodes().forEach { (parent, node, distance) ->
                        output.put(
                            SHMTreeNodeToPrint(
                                node.content,
                                shmTree.rootInfo,
                                reader.assemblerParameters,
                                reader.alignerParameters
                            ) { geneId -> libraryRegistry.getGene(geneId) }
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val COMMAND_NAME = "shm_tree_export_table"
    }
}
