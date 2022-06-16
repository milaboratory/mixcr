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
package com.milaboratory.mixcr.trees

import com.milaboratory.core.Range
import com.milaboratory.core.mutations.Mutations
import com.milaboratory.core.sequence.NucleotideSequence

@Suppress("PropertyName")
class MutationsFromVJGermline(
    val VMutations: VGeneMutations,
    val knownVMutationsWithinNDN: Pair<Mutations<NucleotideSequence>, Range>,
    val knownNDN: NucleotideSequence,
    val knownJMutationsWithinNDN: Pair<Mutations<NucleotideSequence>, Range>,
    val JMutations: JGeneMutations
) {
    val VJMutationsCount: Int
        get() = VMutations.mutationsCount() + JMutations.mutationsCount()
}
