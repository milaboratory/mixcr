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

import com.milaboratory.app.ValidationException
import io.repseq.core.Chains
import io.repseq.core.VDJCGene

fun ValidationException.Companion.chainsExist(chains: Chains?, usedGenes: Collection<VDJCGene>) {
    if (chains != null && !chains.isEmpty) {
        val possibleChains = usedGenes.map { it.chains }.distinct().reduce { a, b -> a.merge(b) }
        require(!chains.intersection(possibleChains).isEmpty) {
            "Chain `$chains` is not presented in input file. Possible values: $possibleChains"
        }
    }
}
