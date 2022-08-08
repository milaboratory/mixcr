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
import com.milaboratory.mixcr.util.VJPair
import io.repseq.core.GeneFeature
import java.util.*
import kotlin.math.max
import kotlin.math.min

//TODO rework model that there is no need of explicit NDN range
@Suppress("PropertyName")
class MutationsFromVJGermline(
    /**
     * Mutations outside of CDR3
     */
    val mutations: VJPair<SortedMap<GeneFeature, Mutations<NucleotideSequence>>>,
    //TODO remove
    /**
     * Already known from alignments V and J mutations within CDR3 feature
     */
    val knownMutationsWithinCDR3: VJPair<Pair<Mutations<NucleotideSequence>, Range>>,
    /**
     * Full sequence of CDR3
     */
    val CDR3: NucleotideSequence,
) {
    val VJMutationsCount: Int
        get() = mutations.V.values.sumOf { it.size() } + mutations.J.values.sumOf { it.size() }

    val VEndTrimmedPosition = min(knownMutationsWithinCDR3.V.second.length(), CDR3.size())
    val JBeginTrimmedPosition = max(CDR3.size() - knownMutationsWithinCDR3.J.second.length(), 0)
}
