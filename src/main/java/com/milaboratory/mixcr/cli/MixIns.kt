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
import io.repseq.core.ReferencePoint
import picocli.CommandLine.Option

interface AlignMiXCRMixIns : MiXCRMixInSet {
    @Option(names = ["+dna"], arity = "0")
    private fun dna(f: Boolean) =
        modifyAlignmentParams(GeneType.Variable, 20, Flags.MaterialType) {
            it.geneFeatureToAlign = GeneFeature.VGeneWithP
        }

    @Option(names = ["+rna"], arity = "0")
    private fun rna(f: Boolean) =
        modifyAlignmentParams(GeneType.Variable, 20, Flags.MaterialType) {
            it.geneFeatureToAlign = GeneFeature.VTranscriptWithP
        }

    private fun setLeftAlignmentBoundary(floating: Boolean, referencePoint: String) {
        val refPoint = ReferencePoint.parse(referencePoint)
        if (refPoint.geneType != GeneType.Variable)
            throw RuntimeException("$refPoint is not inside the V gene")
        modifyAlignmentParams(GeneType.Variable, 10, Flags.LeftAlignmentMode) {
            if (!it.geneFeatureToAlign.contains(refPoint))
                throw RuntimeException("Can't apply mixin because ${it.geneFeatureToAlign} does not contain $refPoint")
            it.geneFeatureToAlign = it.geneFeatureToAlign.splitBy(refPoint).right
            it.parameters.isFloatingLeftBound = floating
        }
    }

    private fun setRightAlignmentBoundary(floating: Boolean, referencePoint: String) {
        val refPoint = ReferencePoint.parse(referencePoint)

        if (refPoint.geneType != GeneType.Joining && refPoint.geneType != GeneType.Constant)
            throw RuntimeException("$refPoint is not inside the J or C gene")

        if (refPoint.geneType == GeneType.Joining) {
            modifyAlignmentParams(GeneType.Joining, 10, Flags.RightAlignmentMode) {
                if (!it.geneFeatureToAlign.contains(refPoint))
                    throw RuntimeException("Can't apply mixin because ${it.geneFeatureToAlign} does not contain $refPoint")
                it.geneFeatureToAlign = it.geneFeatureToAlign.splitBy(refPoint).left
                it.parameters.isFloatingRightBound = floating
            }
            turnOffAlignmentFor(GeneType.Constant, 10)
        } else {
            modifyAlignmentParams(GeneType.Joining, 10) {
                it.parameters.isFloatingRightBound = false
            }
            modifyAlignmentParams(GeneType.Constant, 10, Flags.RightAlignmentMode) {
                if (!it.geneFeatureToAlign.contains(refPoint))
                    throw RuntimeException("Can't apply mixin because ${it.geneFeatureToAlign} does not contain $refPoint")
                it.geneFeatureToAlign = it.geneFeatureToAlign.splitBy(refPoint).left
                it.parameters.isFloatingRightBound = floating
            }
        }
    }

    private fun modifyAlignmentParams(
        gt: GeneType,
        priority: Int,
        vararg flags: String,
        action: (KGeneAlignmentParameters) -> Unit
    ) {
        mixIn {
            setPriority(priority)
            flags.forEach { dropFlag(it) }
            MiXCRParamsBundle::align.update {
                CommandAlign.Params::parameters.applyAfterClone(VDJCAlignerParameters::clone) {
                    val p = getVJCGeneAlignerParameters(gt)
                    action(p)
                }
            }
        }
    }

    private fun turnOffAlignmentFor(
        gt: GeneType,
        priority: Int
    ) {
        mixIn {
            setPriority(priority)
            MiXCRParamsBundle::align.update {
                CommandAlign.Params::parameters.applyAfterClone(VDJCAlignerParameters::clone) {
                    setGeneAlignerParameters(gt, null)
                }
            }
        }
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

class AllMiXCRMixIns : MiXCRMixInCollector(), AssembleMiXCRMixIns