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

import cc.redberry.pipe.CUtils
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.cli.CommandExport.FieldData
import com.milaboratory.mixcr.cli.CommandExport.extractor
import com.milaboratory.mixcr.export.*
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

            val libraryRegistry = VDJCLibraryRegistry.getDefault()
            SHMTreesReader(inputFiles.first(), libraryRegistry).use { reader ->
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
                    .flatMap { extractor(it, Clone::class.java, reader, oMode) }
                    .map { fieldExtractor ->
                        object : FieldExtractor<SHMTreeNodeToPrint> {
                            override fun getHeader() = fieldExtractor.header

                            override fun extractValue(`object`: SHMTreeNodeToPrint): String {
                                val clone = `object`.clone ?: return ""
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
                    //TODO all V mutations and all J mutations (choose feature from params)
                    FieldData.mk("-nMutations", "FR1", "root"),
                    FieldData.mk("-nMutations", "CDR1", "root"),
                    FieldData.mk("-nMutations", "FR2", "root"),
                    FieldData.mk("-nMutations", "CDR2", "root"),
                    FieldData.mk("-nMutations", "FR3", "root"),
                    FieldData.mk("-nMutations", "CDR3", "root"),
                    FieldData.mk("-nMutations", "FR4", "root"),
                    FieldData.mk("-nMutationsRelative", "FR1", "FR1", "root"),
                    FieldData.mk("-nMutationsRelative", "CDR1", "CDR1", "root"),
                    FieldData.mk("-nMutationsRelative", "FR2", "FR2", "root"),
                    FieldData.mk("-nMutationsRelative", "CDR2", "CDR2", "root"),
                    FieldData.mk("-nMutationsRelative", "FR3", "FR3", "root"),
                    FieldData.mk("-nMutationsRelative", "FR4", "FR4", "root"),
                    FieldData.mk("-aaMutations", "FR1", "root"),
                    FieldData.mk("-aaMutations", "CDR1", "root"),
                    FieldData.mk("-aaMutations", "FR2", "root"),
                    FieldData.mk("-aaMutations", "CDR2", "root"),
                    FieldData.mk("-aaMutations", "FR3", "root"),
                    FieldData.mk("-aaMutations", "CDR3", "root"),
                    FieldData.mk("-aaMutations", "FR4", "root"),
                    FieldData.mk("-aaMutationsRelative", "FR1", "FR1", "root"),
                    FieldData.mk("-aaMutationsRelative", "CDR1", "CDR1", "root"),
                    FieldData.mk("-aaMutationsRelative", "FR2", "FR2", "root"),
                    FieldData.mk("-aaMutationsRelative", "CDR2", "CDR2", "root"),
                    FieldData.mk("-aaMutationsRelative", "FR3", "FR3", "root"),
                    FieldData.mk("-aaMutationsRelative", "FR4", "FR4", "root"),
                    FieldData.mk("-mutationsDetailed", "FR1", "root"),
                    FieldData.mk("-mutationsDetailed", "CDR1", "root"),
                    FieldData.mk("-mutationsDetailed", "FR2", "root"),
                    FieldData.mk("-mutationsDetailed", "CDR2", "root"),
                    FieldData.mk("-mutationsDetailed", "FR3", "root"),
                    FieldData.mk("-mutationsDetailed", "CDR3", "root"),
                    FieldData.mk("-mutationsDetailed", "FR4", "root"),
                    FieldData.mk("-mutationsDetailedRelative", "FR1", "FR1", "root"),
                    FieldData.mk("-mutationsDetailedRelative", "CDR1", "CDR1", "root"),
                    FieldData.mk("-mutationsDetailedRelative", "FR2", "FR2", "root"),
                    FieldData.mk("-mutationsDetailedRelative", "CDR2", "CDR2", "root"),
                    FieldData.mk("-mutationsDetailedRelative", "FR3", "FR3", "root"),
                    FieldData.mk("-mutationsDetailedRelative", "FR4", "FR4", "root"),
                    FieldData.mk("-nMutations", "FR1", "mrca"),
                    FieldData.mk("-nMutations", "CDR1", "mrca"),
                    FieldData.mk("-nMutations", "FR2", "mrca"),
                    FieldData.mk("-nMutations", "CDR2", "mrca"),
                    FieldData.mk("-nMutations", "FR3", "mrca"),
                    FieldData.mk("-nMutations", "CDR3", "mrca"),
                    FieldData.mk("-nMutations", "FR4", "mrca"),
                    FieldData.mk("-nMutationsRelative", "FR1", "FR1", "mrca"),
                    FieldData.mk("-nMutationsRelative", "CDR1", "CDR1", "mrca"),
                    FieldData.mk("-nMutationsRelative", "FR2", "FR2", "mrca"),
                    FieldData.mk("-nMutationsRelative", "CDR2", "CDR2", "mrca"),
                    FieldData.mk("-nMutationsRelative", "FR3", "FR3", "mrca"),
                    FieldData.mk("-nMutationsRelative", "FR4", "FR4", "mrca"),
                    FieldData.mk("-aaMutations", "FR1", "mrca"),
                    FieldData.mk("-aaMutations", "CDR1", "mrca"),
                    FieldData.mk("-aaMutations", "FR2", "mrca"),
                    FieldData.mk("-aaMutations", "CDR2", "mrca"),
                    FieldData.mk("-aaMutations", "FR3", "mrca"),
                    FieldData.mk("-aaMutations", "CDR3", "mrca"),
                    FieldData.mk("-aaMutations", "FR4", "mrca"),
                    FieldData.mk("-aaMutationsRelative", "FR1", "FR1", "mrca"),
                    FieldData.mk("-aaMutationsRelative", "CDR1", "CDR1", "mrca"),
                    FieldData.mk("-aaMutationsRelative", "FR2", "FR2", "mrca"),
                    FieldData.mk("-aaMutationsRelative", "CDR2", "CDR2", "mrca"),
                    FieldData.mk("-aaMutationsRelative", "FR3", "FR3", "mrca"),
                    FieldData.mk("-aaMutationsRelative", "FR4", "FR4", "mrca"),
                    FieldData.mk("-mutationsDetailed", "FR1", "mrca"),
                    FieldData.mk("-mutationsDetailed", "CDR1", "mrca"),
                    FieldData.mk("-mutationsDetailed", "FR2", "mrca"),
                    FieldData.mk("-mutationsDetailed", "CDR2", "mrca"),
                    FieldData.mk("-mutationsDetailed", "FR3", "mrca"),
                    FieldData.mk("-mutationsDetailed", "CDR3", "mrca"),
                    FieldData.mk("-mutationsDetailed", "FR4", "mrca"),
                    FieldData.mk("-mutationsDetailedRelative", "FR1", "FR1", "mrca"),
                    FieldData.mk("-mutationsDetailedRelative", "CDR1", "CDR1", "mrca"),
                    FieldData.mk("-mutationsDetailedRelative", "FR2", "FR2", "mrca"),
                    FieldData.mk("-mutationsDetailedRelative", "CDR2", "CDR2", "mrca"),
                    FieldData.mk("-mutationsDetailedRelative", "FR3", "FR3", "mrca"),
                    FieldData.mk("-mutationsDetailedRelative", "FR4", "FR4", "mrca"),
                    FieldData.mk("-nMutations", "FR1", "parent"),
                    FieldData.mk("-nMutations", "CDR1", "parent"),
                    FieldData.mk("-nMutations", "FR2", "parent"),
                    FieldData.mk("-nMutations", "CDR2", "parent"),
                    FieldData.mk("-nMutations", "FR3", "parent"),
                    FieldData.mk("-nMutations", "CDR3", "parent"),
                    FieldData.mk("-nMutations", "FR4", "parent"),
                    FieldData.mk("-nMutationsRelative", "FR1", "FR1", "parent"),
                    FieldData.mk("-nMutationsRelative", "CDR1", "CDR1", "parent"),
                    FieldData.mk("-nMutationsRelative", "FR2", "FR2", "parent"),
                    FieldData.mk("-nMutationsRelative", "CDR2", "CDR2", "parent"),
                    FieldData.mk("-nMutationsRelative", "FR3", "FR3", "parent"),
                    FieldData.mk("-nMutationsRelative", "FR4", "FR4", "parent"),
                    FieldData.mk("-aaMutations", "FR1", "parent"),
                    FieldData.mk("-aaMutations", "CDR1", "parent"),
                    FieldData.mk("-aaMutations", "FR2", "parent"),
                    FieldData.mk("-aaMutations", "CDR2", "parent"),
                    FieldData.mk("-aaMutations", "FR3", "parent"),
                    FieldData.mk("-aaMutations", "CDR3", "parent"),
                    FieldData.mk("-aaMutations", "FR4", "parent"),
                    FieldData.mk("-aaMutationsRelative", "FR1", "FR1", "parent"),
                    FieldData.mk("-aaMutationsRelative", "CDR1", "CDR1", "parent"),
                    FieldData.mk("-aaMutationsRelative", "FR2", "FR2", "parent"),
                    FieldData.mk("-aaMutationsRelative", "CDR2", "CDR2", "parent"),
                    FieldData.mk("-aaMutationsRelative", "FR3", "FR3", "parent"),
                    FieldData.mk("-aaMutationsRelative", "FR4", "FR4", "parent"),
                    FieldData.mk("-mutationsDetailed", "FR1", "parent"),
                    FieldData.mk("-mutationsDetailed", "CDR1", "parent"),
                    FieldData.mk("-mutationsDetailed", "FR2", "parent"),
                    FieldData.mk("-mutationsDetailed", "CDR2", "parent"),
                    FieldData.mk("-mutationsDetailed", "FR3", "parent"),
                    FieldData.mk("-mutationsDetailed", "CDR3", "parent"),
                    FieldData.mk("-mutationsDetailed", "FR4", "parent"),
                    FieldData.mk("-mutationsDetailedRelative", "FR1", "FR1", "parent"),
                    FieldData.mk("-mutationsDetailedRelative", "CDR1", "CDR1", "parent"),
                    FieldData.mk("-mutationsDetailedRelative", "FR2", "FR2", "parent"),
                    FieldData.mk("-mutationsDetailedRelative", "CDR2", "CDR2", "parent"),
                    FieldData.mk("-mutationsDetailedRelative", "FR3", "FR3", "parent"),
                    FieldData.mk("-mutationsDetailedRelative", "FR4", "FR4", "parent"),
                ).flatMap { SHNTreeNodeFieldsExtractor.extract(it, SHMTreeNodeToPrint::class.java, reader, oMode) }

                output.attachInfoProviders(nodeExtractors)

                output.ensureHeader()

                CUtils.it(reader.readTrees()).forEach { shmTree ->
                    val mostRecentCommonAncestor = shmTree.tree.root.content
                    shmTree.tree.allNodes().forEach { (parent, node, distance) ->
                        output.put(
                            SHMTreeNodeToPrint(
                                node.content,
                                parent?.content,
                                mostRecentCommonAncestor,
                                distance,
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
        const val COMMAND_NAME = "exportShmTrees"
    }
}
