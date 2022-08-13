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
package com.milaboratory.mixcr.cli.analyze

import io.repseq.core.Chains

class CommandAnalyzeBioMed2 : AnalyzeBase() {
    override fun run0() {
        alignOps += """
            --species HomoSapiens
            --preset kAligner2
            -OvParameters.geneFeatureToAlign={CDR1Begin:VEnd}+{VEnd:VEnd(-20)}
            -OjParameters.geneFeatureToAlign={JBegin(20):JBegin}+{JBegin:FR4Begin(9)}
            -OvParameters.parameters.floatingLeftBound=false
            -OjParameters.parameters.floatingRightBound=false
        """.trimIndent()

        assembleOps += """
            -OassemblingFeatures="{CDR1Begin:FR4Begin(9)}"
            -OseparateByV=true
            -OseparateByJ=true
        """.trimIndent()

        ///// running
        val vdjca = runAlign(output = "$prefix.vdjca")
        val clnx = runAssemble(input = vdjca, output = "$prefix.clns")
        if (!noExport)
            runExportPerEachChain(Chains.IG, clnx)
    }

    override fun getInputFiles() = emptyList<String>()
    override fun getOutputFiles() = emptyList<String>()
}