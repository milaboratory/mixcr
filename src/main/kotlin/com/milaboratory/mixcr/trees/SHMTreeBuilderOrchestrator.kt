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
@file:Suppress("PrivatePropertyName", "LocalVariableName", "FunctionName")

package com.milaboratory.mixcr.trees

import cc.redberry.pipe.OutputPort
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.mutations.Mutations.EMPTY_NUCLEOTIDE_MUTATIONS
import com.milaboratory.core.sequence.NucleotideSequence
import com.milaboratory.mitool.pattern.search.BasicSerializer
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.ClonesSupplier
import com.milaboratory.mixcr.basictypes.GeneFeatures
import com.milaboratory.mixcr.basictypes.HasFeatureToAlign
import com.milaboratory.mixcr.util.XSV
import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.annotations.Serializable
import com.milaboratory.primitivio.flatten
import com.milaboratory.primitivio.map
import com.milaboratory.primitivio.readObjectRequired
import com.milaboratory.util.ProgressAndStage
import com.milaboratory.util.TempFileDest
import io.repseq.core.GeneFeature.CDR3
import io.repseq.core.VDJCGene
import io.repseq.core.VDJCGeneId
import java.io.File
import java.io.PrintStream
import java.nio.file.Path


class SHMTreeBuilderOrchestrator(
    private val parameters: SHMTreeBuilderParameters,
    private val scoringSet: ScoringSet,
    private val datasets: List<ClonesSupplier>,
    private val featureToAlign: HasFeatureToAlign,
    private val usedGenes: Collection<VDJCGene>,
    private val assemblingFeatures: GeneFeatures,
    private val tempDest: TempFileDest,
    private val debugDirectory: Path,
    VGenesToSearch: Set<String>,
    JGenesToSearch: Set<String>,
    CDR3LengthToSearch: Set<Int>,
    minCountForClone: Int?
) {
    private val clonesFilter = ClonesFilter(
        VGenesToSearch.ifEmpty { null },
        JGenesToSearch.ifEmpty { null },
        CDR3LengthToSearch.ifEmpty { null },
        minCountForClone,
        parameters.productiveOnly
    )

    private val SHMTreeBuilder = SHMTreeBuilder(
        parameters.topologyBuilder,
        scoringSet
    )

    /**
     * @param userInput (datasetId:cloneId) = treeId
     */
    fun buildByUserData(
        userInput: Map<CloneWithDatasetId.ID, Int>,
        threads: Int
    ): OutputPort<TreeWithMetaBuilder> {
        val treeBuilder = TreeBuilderByUserData(
            tempDest,
            featureToAlign.constructStateBuilder(usedGenes),
            assemblingFeatures,
            SHMTreeBuilder
        )
        return readClonesWithDatasetIds { clones ->
            treeBuilder.buildByUserData(clones, userInput, threads)
        }
    }


    fun buildTreesByCellTags(
        singleCellParams: SHMTreeBuilderParameters.SingleCell.SimpleClustering,
        threads: Int,
        resultWriter: (OutputPort<TreeWithMetaBuilder>) -> Unit
    ) {
        check(datasets.map { it.tagsInfo }.distinct().size == 1) {
            "tagsInfo must be the same for all files"
        }
        val tagsInfo = datasets.first().tagsInfo
        val stateBuilder = featureToAlign.constructStateBuilder(usedGenes)

        val treeBuilder = SingleCellTreeBuilder(
            singleCellParams,
            stateBuilder,
            tempDest,
            clonesFilter,
            scoringSet,
            assemblingFeatures,
            SHMTreeBuilder
        )
        readClonesWithDatasetIds { clones ->
            treeBuilder.buildTrees(clones, tagsInfo, threads, resultWriter)
        }
    }

    fun buildTreesBySteps(
        progressAndStage: ProgressAndStage,
        reportBuilder: BuildSHMTreeReport.Builder,
        threads: Int,
        resultWriter: (OutputPort<TreeWithMetaBuilder>) -> Unit
    ) {
        val treeBuilder = SHMTreeBuilderBySteps(
            parameters.steps,
            scoringSet,
            assemblingFeatures,
            SHMTreeBuilder,
            clonesFilter,
            relatedAllelesMutations(),
            datasets.sumOf { it.numberOfClones() }.toLong(),
            featureToAlign.constructStateBuilder(usedGenes),
            tempDest
        )
        val debugs = parameters.steps.indices.map {
            createDebug(it)
        }
        readClonesWithDatasetIds { clones ->
            treeBuilder.buildTrees(
                clones,
                progressAndStage,
                threads,
                debugs,
                resultWriter
            )
        }

        debugs.forEachIndexed { i, debug ->
            reportBuilder.addStatsForStep(
                parameters.steps[i],
                debug,
                if (i == 0) null else debugs[i - 1]
            )
        }
    }

    //TODO auto close through lambda
    private fun <R> readClonesWithDatasetIds(function: (OutputPort<CloneWithDatasetId>) -> R) = datasets
        .withIndex()
        .map { (datasetId, dataset) ->
            dataset.readClones().map { clone -> CloneWithDatasetId(clone, datasetId) }
        }
        .flatten()
        .use(function)

    /**
     * For every gene make a list of mutations to alleles of the gene.
     * Empty list if no alleles for the gene.
     */
    private fun relatedAllelesMutations(): Map<VDJCGeneId, List<Mutations<NucleotideSequence>>> = usedGenes
        .groupBy { it.geneName }
        .values
        .flatMap { allelesGenes ->
            when (allelesGenes.size) {
                1 -> emptyList()
                else -> allelesGenes.map { gene ->
                    val currentAlleleMutations = alleleMutations(gene)
                    gene.id to (allelesGenes - gene)
                        .map { currentAlleleMutations.invert().combineWith(alleleMutations(it)) }
                }
            }
        }
        .toMap()

    private fun alleleMutations(gene: VDJCGene): Mutations<NucleotideSequence> =
        gene.data.baseSequence.mutations ?: EMPTY_NUCLEOTIDE_MUTATIONS

    private fun createDebug(stepNumber: Int): Debug {
        val beforeDecisions = prepareDebugFile(stepNumber, "before_decisions")
        val afterDecisions = prepareDebugFile(stepNumber, "after_decisions")
        return Debug(
            beforeDecisions.first,
            beforeDecisions.second,
            afterDecisions.first,
            afterDecisions.second
        )
    }

    private fun prepareDebugFile(stepNumber: Int, suffix: String): Pair<File, PrintStream> {
        val debugFile = debugDirectory.resolve("step_" + stepNumber + "_" + suffix + ".csv").toFile()
        debugFile.delete()
        debugFile.createNewFile()
        val debugWriter = PrintStream(debugFile)
        XSV.writeXSVHeaders(debugWriter, DebugInfo.COLUMNS_FOR_XSV.keys, ";")
        return debugFile to debugWriter
    }

    class Debug(
        val treesBeforeDecisionsFile: File,
        val treesBeforeDecisionsWriter: PrintStream,
        val treesAfterDecisionsFile: File,
        val treesAfterDecisionsWriter: PrintStream
    )

    class ClonesFilter(
        private val VGenesToSearch: Set<String>?,
        private val JGenesToSearch: Set<String>?,
        private val CDR3LengthToSearch: Set<Int>?,
        private val minCountForClone: Int?,
        private val productiveOnly: Boolean
    ) {
        // filter non-productive clonotypes
        // todo only CDR3?
        fun matchForProductive(clone: Clone) = !productiveOnly ||
                (!clone.containsStopsOrAbsent(CDR3) && !clone.isOutOfFrameOrAbsent(CDR3))

        // filter non-productive clonotypes
        // todo only CDR3?
        fun matchForProductive(clone: Clone, VJBase: VJBase) = !productiveOnly ||
                (!clone.containsStopsOrAbsent(CDR3, VJBase) && !clone.isOutOfFrameOrAbsent(CDR3, VJBase))

        fun match(VJBase: VJBase): Boolean =
            (VGenesToSearch?.contains(VJBase.geneIds.V.name) ?: true) &&
                    (JGenesToSearch?.contains(VJBase.geneIds.J.name) ?: true) &&
                    CDR3LengthMatches(VJBase)

        fun match(cloneWrapper: CloneWrapper): Boolean =
            VGeneMatches(cloneWrapper) && JGeneMatches(cloneWrapper) && CDR3LengthMatches(cloneWrapper.VJBase)
                    && cloneWrapper.clones.any { countMatches(it.clone) }

        fun countMatches(clone: Clone): Boolean {
            if (minCountForClone == null) return true
            return clone.count >= minCountForClone
        }

        private fun JGeneMatches(cloneWrapper: CloneWrapper): Boolean {
            if (JGenesToSearch == null) return true
            return cloneWrapper.candidateVJBases.any { it.geneIds.J.name in JGenesToSearch }
        }

        private fun VGeneMatches(cloneWrapper: CloneWrapper): Boolean {
            if (VGenesToSearch == null) return true
            return cloneWrapper.candidateVJBases.any { it.geneIds.V.name in VGenesToSearch }
        }

        private fun CDR3LengthMatches(VJBase: VJBase) =
            (CDR3LengthToSearch?.contains(VJBase.CDR3length) ?: true)
    }
}

@Serializable(by = CloneFromUserInput.SerializerImpl::class)
class CloneFromUserInput(
    val clone: Clone,
    val datasetId: Int,
    val treeId: Int
) {
    class SerializerImpl : BasicSerializer<CloneFromUserInput>() {
        override fun write(output: PrimitivO, obj: CloneFromUserInput) {
            output.writeObject(obj.clone)
            output.writeInt(obj.datasetId)
            output.writeInt(obj.treeId)
        }

        override fun read(input: PrimitivI): CloneFromUserInput {
            val clone = input.readObjectRequired<Clone>()
            val datasetId = input.readInt()
            val treeId = input.readInt()
            return CloneFromUserInput(
                clone,
                datasetId,
                treeId
            )
        }
    }
}
