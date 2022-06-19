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
package com.milaboratory.mixcr.export

import com.milaboratory.mixcr.trees.SHMTreeForPostanalysis
import io.repseq.core.GeneType

object SHNTreeFieldsExtractor : BaseFieldExtractors() {
    override fun initFields(): Array<Field<out Any>> {
        val fields = mutableListOf<Field<SHMTreeForPostanalysis>>()

        fields += FieldParameterless(
            "-treeId",
            "SHM tree id",
            "Tree id",
            "treeId"
        ) { it.meta.treeId.encode() }

        // Best hits
        for (type in arrayOf(GeneType.Variable, GeneType.Joining)) {
            val l = type.letter
            fields.add(FieldParameterless(
                "-${l.lowercaseChar()}Hit",
                "Export best $l hit",
                "Best $l hit",
                "best${l}Hit"
            ) {
                it.meta.rootInfo.VJBase.getGeneId(type).name
            })
        }

        return fields.toTypedArray()
    }
}
