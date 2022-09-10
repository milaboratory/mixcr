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

import com.milaboratory.mixcr.MiXCRParamsBundle
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters
import com.milaboratory.mixcr.basictypes.GeneFeatures
import picocli.CommandLine.Option

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