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
import io.repseq.core.GeneFeature
import java.util.*

@Suppress("PropertyName")
class MutationsFromVJGermline(
    val VMutations: SortedMap<GeneFeature, Mutations<NucleotideSequence>>,
    /**
     * Already known from alignments V mutations within CDR3 feature
     */
    val knownVMutationsWithinCDR3: Pair<Mutations<NucleotideSequence>, Range>,
    /**
     * Full sequence of CDR3
     */
    val CDR3: NucleotideSequence,
    /**
     * Already known from alignments J mutations within CDR3 feature
     */
    val knownJMutationsWithinCDR3: Pair<Mutations<NucleotideSequence>, Range>,
    val JMutations: SortedMap<GeneFeature, Mutations<NucleotideSequence>>
) {
    val VJMutationsCount: Int
        get() = VMutations.values.sumOf { it.size() } + JMutations.values.sumOf { it.size() }

    val VEndTrimmedPosition = knownVMutationsWithinCDR3.second.length()
    val JBeginTrimmedPosition = CDR3.size() - knownJMutationsWithinCDR3.second.length()
}
