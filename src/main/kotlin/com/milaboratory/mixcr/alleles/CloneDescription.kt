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
package com.milaboratory.mixcr.alleles

import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence

data class CloneDescription(
    /**
     * Mutations of clone without CDR3
     */
    val mutations: Mutations<NucleotideSequence>,
    val clusterIdentity: ClusterIdentity
) {

    data class ClusterIdentity(
        private val CDR3Length: Int,
        private val complimentaryGeneName: String
    )
}
