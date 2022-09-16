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

import com.milaboratory.mixcr.*
import com.milaboratory.mixcr.basictypes.GeneFeatures
import io.repseq.core.GeneType.Constant
import io.repseq.core.GeneType.Joining
import io.repseq.core.ReferencePoint
import picocli.CommandLine.Option

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

interface AssembleMiXCRMixIns : MiXCRMixinSet {
    @Option(names = [SetClonotypeAssemblingFeatures.CMD_OPTION])
    fun assembleClonotypesBy(gf: String) =
        mixIn(SetClonotypeAssemblingFeatures(GeneFeatures.parse(gf)))
}

class AllMiXCRMixins : MiXCRMixinCollector(), PipelineMiXCRMixins, AlignMiXCRMixins, AssembleMiXCRMixIns
