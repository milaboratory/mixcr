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

class NodeMutationsDescription(
    val VMutationsWithoutCDR3: Map<Range, MutationsWithRange>,
    val VMutationsInCDR3WithoutNDN: MutationsWithRange,
    val knownNDN: MutationsWithRange,
    val JMutationsInCDR3WithoutNDN: MutationsWithRange,
    val JMutationsWithoutCDR3: Map<Range, MutationsWithRange>
)
