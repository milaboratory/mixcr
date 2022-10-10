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

import com.milaboratory.cli.ValidationException
import com.milaboratory.mixcr.*
import com.milaboratory.mixcr.basictypes.GeneFeatures
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Constant
import io.repseq.core.GeneType.Joining
import io.repseq.core.ReferencePoint
import picocli.CommandLine.IParameterConsumer
import picocli.CommandLine.Model.ArgSpec
import picocli.CommandLine.Model.CommandSpec
import picocli.CommandLine.Option
import java.util.*

interface MiXCRMixinSet {
    fun mixIn(mixin: MiXCRMixin)
}

interface PipelineMiXCRMixins : MiXCRMixinSet {
    //
    // Pipeline manipulation mixins
    //

    @Option(names = [AddPipelineStep.CMD_OPTION])
    fun addPipelineStep(step: String) =
        mixIn(AddPipelineStep(step))

    @Option(names = [RemovePipelineStep.CMD_OPTION])
    fun removePipelineStep(step: String) =
        mixIn(RemovePipelineStep(step))
}

interface TagMiXCRMixins : MiXCRMixinSet {
    //
    // Base tag-related settings
    //

    @Option(names = [SetTagPattern.CMD_OPTION])
    fun tagPattern(pattern: String) =
        mixIn(SetTagPattern(pattern))
}

interface AlignMiXCRMixins : MiXCRMixinSet, TagMiXCRMixins {
    //
    // Base settings
    //

    @Option(names = [SetSpecies.CMD_OPTION])
    fun species(species: String) =
        mixIn(SetSpecies(species))

    @Option(names = [SetLibrary.CMD_OPTION])
    fun library(library: String) =
        mixIn(SetLibrary(library))

    @Option(names = [LimitInput.CMD_OPTION])
    fun limitInput(number: Long) =
        mixIn(LimitInput(number))

    //
    // Material type
    //

    @Option(names = [MaterialTypeDNA.CMD_OPTION], arity = "0")
    fun dna(f: Boolean) =
        mixIn(MaterialTypeDNA)

    @Option(names = [MaterialTypeRNA.CMD_OPTION], arity = "0")
    fun rna(f: Boolean) =
        mixIn(MaterialTypeRNA)

    //
    // Alignment boundaries
    //

    @Option(names = [AlignmentBoundaryMixinConstants.LEFT_FLOATING_CMD_OPTION], arity = "0..1")
    fun floatingLeftAlignmentBoundary(arg: String?) =
        mixIn(
            if (arg.isNullOrBlank())
                LeftAlignmentBoundaryNoPoint(true)
            else
                LeftAlignmentBoundaryWithPoint(true, ReferencePoint.parse(arg))
        )

    @Option(names = [AlignmentBoundaryMixinConstants.LEFT_RIGID_CMD_OPTION], arity = "0..1")
    fun rigidLeftAlignmentBoundary(arg: String?) =
        mixIn(
            if (arg.isNullOrBlank())
                LeftAlignmentBoundaryNoPoint(false)
            else
                LeftAlignmentBoundaryWithPoint(false, ReferencePoint.parse(arg))
        )

    @Option(names = [AlignmentBoundaryMixinConstants.RIGHT_FLOATING_CMD_OPTION], arity = "1")
    fun floatingRightAlignmentBoundary(arg: String) =
        mixIn(
            when {
                arg.equals("C", ignoreCase = true) || arg.equals("Constant", ignoreCase = true) ->
                    RightAlignmentBoundaryNoPoint(true, Constant)

                arg.equals("J", ignoreCase = true) || arg.equals("Joining", ignoreCase = true) ->
                    RightAlignmentBoundaryNoPoint(true, Joining)

                else ->
                    RightAlignmentBoundaryWithPoint(true, ReferencePoint.parse(arg))
            }
        )

    @Option(names = [AlignmentBoundaryMixinConstants.RIGHT_RIGID_CMD_OPTION], arity = "0..1")
    fun rigidRightAlignmentBoundary(arg: String?) =
        mixIn(
            when {
                arg.isNullOrBlank() ->
                    RightAlignmentBoundaryNoPoint(false, null)

                arg.equals("C", ignoreCase = true) || arg.equals("Constant", ignoreCase = true) ->
                    RightAlignmentBoundaryNoPoint(false, Constant)

                arg.equals("J", ignoreCase = true) || arg.equals("Joining", ignoreCase = true) ->
                    RightAlignmentBoundaryNoPoint(false, Joining)

                else ->
                    RightAlignmentBoundaryWithPoint(false, ReferencePoint.parse(arg))
            }
        )
}

interface AssembleMiXCRMixins : MiXCRMixinSet {
    @Option(names = [SetClonotypeAssemblingFeatures.CMD_OPTION])
    fun assembleClonotypesBy(gf: String) =
        mixIn(SetClonotypeAssemblingFeatures(GeneFeatures.parse(gf)))

    @Option(names = [KeepNonCDR3Alignments.CMD_OPTION], negatable = false, arity = "0")
    fun keepNonCDR3Alignments(ignored: Boolean) =
        mixIn(KeepNonCDR3Alignments)

    @Option(names = [DropNonCDR3Alignments.CMD_OPTION], negatable = false, arity = "0")
    fun dropNonCDR3Alignments(ignored: Boolean) =
        mixIn(DropNonCDR3Alignments)

    @Option(names = [SetSplitClonesBy.CMD_OPTION_TRUE])
    fun splitClonesBy(geneTypes: List<String>) =
        geneTypes.forEach { geneType -> mixIn(SetSplitClonesBy(GeneType.parse(geneType), true)) }

    @Option(names = [SetSplitClonesBy.CMD_OPTION_FALSE], arity = "0")
    fun dontSplitClonesBy(geneTypes: List<String>) =
        geneTypes.forEach { geneType -> mixIn(SetSplitClonesBy(GeneType.parse(geneType), false)) }
}

interface AssembleContigsMiXCRMixins : MiXCRMixinSet {
    @Option(names = [SetContigAssemblingFeatures.CMD_OPTION])
    fun assembleContigsBy(gf: String) =
        mixIn(SetContigAssemblingFeatures(GeneFeatures.parse(gf)))
}

interface ExportMiXCRMixins : MiXCRMixinSet {
    @Option(names = [ImputeGermlineOnExport.CMD_OPTION], arity = "0")
    fun imputeGermlineOnExport(ignored: Boolean) =
        mixIn(ImputeGermlineOnExport)

    @Option(names = [DontImputeGermlineOnExport.CMD_OPTION], arity = "0")
    fun dontImputeGermlineOnExport(ignored: Boolean) =
        mixIn(DontImputeGermlineOnExport)

    private fun addExportClonesField(data: List<String>, prepend: Boolean, args: Int) {
        if (data.isEmpty())
            return
        val sublist = data.takeLast(args + 1)
        mixIn(AddExportClonesField(if (prepend) 0 else -1, sublist[0], sublist.drop(1)))
    }

    private fun addExportAlignmentsField(data: List<String>, prepend: Boolean, args: Int) {
        if (data.isEmpty())
            return
        val sublist = data.takeLast(args + 1)
        mixIn(AddExportAlignmentsField(if (prepend) 0 else -1, sublist[0], sublist.drop(1)))
    }

    @Option(
        names = [AddExportClonesField.CMD_OPTION_PREPEND_PREFIX + "0"],
        parameterConsumer = ExecParameterConsumer0::class,
        arity = "1",
    )
    fun prependExportClonesField0(data: List<String>) =
        addExportClonesField(data, true, 0)

    @Option(
        names = [AddExportClonesField.CMD_OPTION_PREPEND_PREFIX + "1"],
        parameterConsumer = ExecParameterConsumer1::class,
        arity = "2",
    )
    fun prependExportClonesField1(data: List<String>) =
        addExportClonesField(data, true, 1)

    @Option(
        names = [AddExportClonesField.CMD_OPTION_PREPEND_PREFIX + "2"],
        parameterConsumer = ExecParameterConsumer2::class,
        arity = "3",
    )
    fun prependExportClonesField2(data: List<String>) =
        addExportClonesField(data, true, 2)

    @Option(
        names = [AddExportClonesField.CMD_OPTION_PREPEND_PREFIX + "3"],
        parameterConsumer = ExecParameterConsumer3::class,
        arity = "4"
    )
    fun prependExportClonesField3(data: List<String>) =
        addExportClonesField(data, true, 3)

    @Option(
        names = [AddExportClonesField.CMD_OPTION_APPEND_PREFIX + "0"],
        parameterConsumer = ExecParameterConsumer0::class,
        arity = "1"
    )
    fun appendExportClonesField0(data: List<String>) =
        addExportClonesField(data, false, 0)

    @Option(
        names = [AddExportClonesField.CMD_OPTION_APPEND_PREFIX + "1"],
        parameterConsumer = ExecParameterConsumer1::class,
        arity = "2"
    )
    fun appendExportClonesField1(data: List<String>) =
        addExportClonesField(data, false, 1)

    @Option(
        names = [AddExportClonesField.CMD_OPTION_APPEND_PREFIX + "2"],
        parameterConsumer = ExecParameterConsumer2::class,
        arity = "3"
    )
    fun appendExportClonesField2(data: List<String>) =
        addExportClonesField(data, false, 2)

    @Option(
        names = [AddExportClonesField.CMD_OPTION_APPEND_PREFIX + "3"],
        parameterConsumer = ExecParameterConsumer3::class,
        arity = "4"
    )
    fun appendExportClonesField3(data: List<String>) =
        addExportClonesField(data, false, 3)

    companion object {
        abstract class ExecParameterConsumer(private val nArgs: Int) : IParameterConsumer {
            override fun consumeParameters(args: Stack<String>, argSpec: ArgSpec, commandSpec: CommandSpec) {
                val list: MutableList<String> = mutableListOf()
                var i = nArgs + 1
                while (!args.isEmpty() && i > 0) {
                    list.add(args.pop())
                    --i
                }
                if (list.size != (nArgs + 1))
                    throw ValidationException(
                        commandSpec.commandLine(),
                        "${nArgs + 1} parameters expected but only ${list.size} were supplied",
                        true
                    )
                argSpec.setValue(list)
            }
        }

        class ExecParameterConsumer0 : ExecParameterConsumer(0)
        class ExecParameterConsumer1 : ExecParameterConsumer(1)
        class ExecParameterConsumer2 : ExecParameterConsumer(2)
        class ExecParameterConsumer3 : ExecParameterConsumer(3)
    }
}

interface GenericMiXCRMixins : MiXCRMixinSet {
    @Option(names = [GenericMixin.CMD_OPTION])
    fun genericMixin(fieldAndOverrides: Map<String, String>) {
        fieldAndOverrides.forEach { (field, override) ->
            mixIn(GenericMixin(field, override))
        }
    }
}

class AllMiXCRMixins : MiXCRMixinCollector(), PipelineMiXCRMixins,
    AlignMiXCRMixins, AssembleMiXCRMixins, AssembleContigsMiXCRMixins,
    ExportMiXCRMixins, GenericMiXCRMixins

class AllExportMiXCRMixins : MiXCRMixinCollector(), ExportMiXCRMixins