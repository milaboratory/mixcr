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

data class TreeId(
    val id: Int,
    val VJBase: VJBase
) {
    fun encode(): String = "${VJBase.geneIds.V.name}-${VJBase.CDR3length}-${VJBase.geneIds.J.name}-${id}"

    companion object {
        val comparator: Comparator<TreeId> = Comparator
            .comparing({ treeId: TreeId -> treeId.VJBase }, VJBase.comparator)
            .thenComparingInt { treeId: TreeId -> treeId.id }
    }
}
