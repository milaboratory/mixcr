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

import com.milaboratory.mixcr.Flags
import com.milaboratory.mixcr.MiXCRParamsBundle
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters
import com.milaboratory.mixcr.basictypes.GeneFeatures
import com.milaboratory.mixcr.vdjaligners.KGeneAlignmentParameters
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import io.repseq.core.GeneFeature
import io.repseq.core.GeneType
import io.repseq.core.GeneType.Constant
import io.repseq.core.GeneType.Joining
import io.repseq.core.ReferencePoint
import picocli.CommandLine.Option

interface AlignMiXCRMixIns : MiXCRMixInSet {
    //
    // Base settings
    //

    @Option(names = ["+species"])
    fun species(species: String) =
        mixIn {
            setPriority(50)
            MiXCRParamsBundle::align.update {
                CommandAlign.Params::species setTo species
            }
        }

    //
    // Material type
    //

    @Option(names = ["+dna"], arity = "0")
    fun dna(f: Boolean) =
        modifyAlignmentParams(20, Flags.MaterialType) {
            // Aligning V gene with intron sequence
            vAlignerParameters.geneFeatureToAlign = GeneFeature.VGeneWithP

            // TODO consider adding genomic sequences downstream J ( or add another mix-in for that ? )

            // C gene is too far from CDR3 in DNA data,
            // there is no need in spending time searching for it
            // NOTE: If such behaviour is still needed, other parameters should also be considered and changed,
            // to achieve expected results
            setGeneAlignerParameters(GeneType.Constant, null)
        }

    @Option(names = ["+rna"], arity = "0")
    fun rna(f: Boolean) =
        modifyGeneAlignmentParams(GeneType.Variable, 20, Flags.MaterialType) {
            // V gene without intron sequence, including 5'UTR sequence
            it.geneFeatureToAlign = GeneFeature.VTranscriptWithP
        }

    //
    // Alignment boundaries
    //

    @Option(names = ["+floatingLeftAlignmentBoundary"], arity = "0..1")
    fun floatingLeftAlignmentBoundary(arg: String?) {
        if (arg.isNullOrBlank())
            setLeftAlignmentSettings(true)
        else
            setLeftAlignmentSettings(true, ReferencePoint.parse(arg))
    }

    @Option(names = ["+rigidLeftAlignmentBoundary"], arity = "0..1")
    fun rigidLeftAlignmentBoundary(arg: String?) {
        if (arg.isNullOrBlank())
            setLeftAlignmentSettings(false)
        else
            setLeftAlignmentSettings(false, ReferencePoint.parse(arg))
    }

    @Option(names = ["+floatingRightAlignmentBoundary"], arity = "1")
    fun floatingRightAlignmentBoundary(arg: String) =
        when {
            arg.equals("C", ignoreCase = true) || arg.equals("Constant", ignoreCase = true) ->
                setRightAlignmentSettings(true, Constant)

            arg.equals("J", ignoreCase = true) || arg.equals("Joining", ignoreCase = true) ->
                setRightAlignmentSettings(true, Joining)

            else ->
                setRightAlignmentSettings(true, ReferencePoint.parse(arg))
        }

    @Option(names = ["+rigidRightAlignmentBoundary"], arity = "0..1")
    fun rigidRightAlignmentBoundary(arg: String?) =
        when {
            arg.isNullOrBlank() ->
                setRightAlignmentSettings(false, null)

            arg.equals("C", ignoreCase = true) || arg.equals("Constant", ignoreCase = true) ->
                setRightAlignmentSettings(false, Constant)

            arg.equals("J", ignoreCase = true) || arg.equals("Joining", ignoreCase = true) ->
                setRightAlignmentSettings(false, Joining)

            else ->
                setRightAlignmentSettings(false, ReferencePoint.parse(arg))
        }

    //
    // Helpers for alignment boundaries
    //

    private fun setLeftAlignmentSettings(floating: Boolean) {
        modifyGeneAlignmentParams(GeneType.Variable, 10, Flags.LeftAlignmentMode) {
            it.parameters.isFloatingLeftBound = floating
        }
    }

    private fun setLeftAlignmentSettings(floating: Boolean, refPoint: ReferencePoint) {
        // Checking parameters
        if (refPoint.geneType != GeneType.Variable)
            throw RuntimeException("$refPoint is not inside the V gene")

        modifyGeneAlignmentParams(GeneType.Variable, 10, Flags.LeftAlignmentMode) {
            if (!it.geneFeatureToAlign.contains(refPoint))
                throw RuntimeException("Can't apply mixin because ${it.geneFeatureToAlign} does not contain $refPoint")
            it.geneFeatureToAlign = it.geneFeatureToAlign.splitBy(refPoint).right
            it.parameters.isFloatingLeftBound = floating
        }
    }

    private fun setRightAlignmentSettings(floating: Boolean, geneType: GeneType?) =
        when (geneType) {
            null -> {
                if (floating)
                    throw IllegalArgumentException() // must be checked in the options
                modifyAlignmentParams(10, Flags.RightAlignmentMode) {
                    jAlignerParameters.parameters.isFloatingRightBound = false
                    cAlignerParameters.parameters.isFloatingRightBound = false
                }
            }

            Joining ->
                modifyAlignmentParams(10, Flags.RightAlignmentMode) {
                    // Setting alignment mode
                    jAlignerParameters.parameters.isFloatingRightBound = floating
                    // And turn off C gene alignment as alignment should terminate somewhere in J gene
                    setGeneAlignerParameters(Constant, null)
                }

            Constant ->
                modifyAlignmentParams(10, Flags.RightAlignmentMode) {
                    // Checking mixin assumptions
                    if (jAlignerParameters.geneFeatureToAlign.lastPoint != ReferencePoint.FR4End)
                        throw RuntimeException(
                            "Incompatible J gene right alignment feature boundary for the mix-in: " +
                                    "${jAlignerParameters.geneFeatureToAlign.lastPoint}"
                        )

                    // And setting strict alignment mode for the J gene
                    jAlignerParameters.parameters.isFloatingRightBound = false
                    // Setting alignment mode
                    cAlignerParameters.parameters.isFloatingRightBound = floating
                }

            else -> throw IllegalArgumentException()
        }

    private fun setRightAlignmentSettings(floating: Boolean, refPoint: ReferencePoint) =
        when (refPoint.geneType) {
            Joining ->
                modifyAlignmentParams(10, Flags.RightAlignmentMode) {
                    // Checking mixin assumptions
                    if (jAlignerParameters.geneFeatureToAlign.lastPoint != ReferencePoint.FR4End &&
                        !jAlignerParameters.geneFeatureToAlign.contains(refPoint)
                    )
                        throw RuntimeException(
                            "Incompatible J gene alignment feature for the mix-in: " +
                                    "${jAlignerParameters.geneFeatureToAlign}"
                        )

                    // Adjusting feature to align
                    jAlignerParameters.geneFeatureToAlign = jAlignerParameters.geneFeatureToAlign.setLastPoint(refPoint)
                    // Setting alignment mode
                    jAlignerParameters.parameters.isFloatingRightBound = floating
                    // And turn off C gene alignment as alignment should terminate somewhere in J gene
                    setGeneAlignerParameters(Constant, null)
                }

            Constant ->
                modifyAlignmentParams(10, Flags.RightAlignmentMode) {
                    // Checking mixin assumptions
                    if (cAlignerParameters.geneFeatureToAlign.lastPoint != ReferencePoint.CExon1End &&
                        !cAlignerParameters.geneFeatureToAlign.contains(refPoint)
                    )
                        throw RuntimeException(
                            "Incompatible C gene alignment feature for the mix-in: " +
                                    "${cAlignerParameters.geneFeatureToAlign}"
                        )
                    if (jAlignerParameters.geneFeatureToAlign.lastPoint != ReferencePoint.FR4End)
                        throw RuntimeException(
                            "Incompatible J gene right alignment feature boundary for the mix-in: " +
                                    "${jAlignerParameters.geneFeatureToAlign.lastPoint}"
                        )

                    // And setting strict alignment mode for the J gene
                    jAlignerParameters.parameters.isFloatingRightBound = false

                    // Adjusting feature to align
                    cAlignerParameters.geneFeatureToAlign = cAlignerParameters.geneFeatureToAlign.setLastPoint(refPoint)
                    // Setting alignment mode
                    cAlignerParameters.parameters.isFloatingRightBound = floating
                }

            else -> throw RuntimeException("$refPoint is not inside the J or C gene")
        }

    //
    // Generic helper methods
    //

    private fun modifyAlignmentParams(
        priority: Int,
        vararg flags: String,
        action: VDJCAlignerParameters.() -> Unit
    ) {
        mixIn {
            setPriority(priority)
            flags.forEach { dropFlag(it) }
            MiXCRParamsBundle::align.update {
                CommandAlign.Params::parameters.applyAfterClone(VDJCAlignerParameters::clone) {
                    action()
                }
            }
        }
    }

    private fun modifyGeneAlignmentParams(
        gt: GeneType,
        priority: Int,
        vararg flags: String,
        action: (KGeneAlignmentParameters) -> Unit
    ) =
        modifyAlignmentParams(priority, *flags) {
            val p = getVJCGeneAlignerParameters(gt)
            if (p != null) // if alignment for the gene is off - do nothing
                action(p)
        }
}

interface AssembleMiXCRMixIns : MiXCRMixInSet {
    @Option(names = ["+assembleClonotypesBy"])
    fun assembleClonotypesBy(gf: String) {
        val geneFeatures = GeneFeatures.parse(gf)
        mixIn {
            MiXCRParamsBundle::assemble.update {
                CommandAssemble.Params::cloneAssemblerParameters.applyAfterClone(CloneAssemblerParameters::clone) {
                    assemblingFeatures = geneFeatures.features
                }
            }
        }
    }
}

class AllMiXCRMixIns : MiXCRMixInCollector(), AlignMiXCRMixIns, AssembleMiXCRMixIns