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

/**
 * All known clone mutations with rebased on specified VEndTrimmed and JEndTrimmed.
 * VEndTrimmed and JEndTrimmed may not be the same as in the clone.
 *
 * specified VEndTrimmed and specified JEndTrimmed are calculated for entire VJ cluster
 * @see ClusterProcessor.CalculatedClusterInfo.VRangeInCDR3
 * @see ClusterProcessor.CalculatedClusterInfo.JRangeInCDR3
 *
 * Assumptions:
 *      specified-VEndTrimmed < clone-VEndTrimmed
 *      specified-JEndTrimmed > clone-JEndTrimmed
 */
@Suppress("PropertyName")
class MutationsFromVJGermline(
    val VMutations: VGeneMutations,
    /**
     * First: mutations within [specified-VEndTrimmed:clone-VEndTrimmed]
     * second: range [specified-VEndTrimmed:clone-VEndTrimmed] in V coordinates
     */
    val knownVMutationsWithinNDN: Pair<Mutations<NucleotideSequence>, Range>,
    /**
     * Part of NDN within [specified-VEndTrimmed:specified-JEndTrimmed]
     */
    val knownNDN: NucleotideSequence,
    /**
     * First: mutations within [clone-JEndTrimmed:specified-JEndTrimmed]
     * second: range [clone-JEndTrimmed:specified-JEndTrimmed] in J coordinates
     */
    val knownJMutationsWithinNDN: Pair<Mutations<NucleotideSequence>, Range>,
    val JMutations: JGeneMutations
) {
    val VJMutationsCount: Int
        get() = VMutations.mutationsCount() + JMutations.mutationsCount()
}
