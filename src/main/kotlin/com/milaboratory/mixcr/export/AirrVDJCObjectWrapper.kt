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

import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.VDJCAlignments
import com.milaboratory.mixcr.basictypes.VDJCObject
import com.milaboratory.mixcr.export.AirrUtil.AirrAlignment

class AirrVDJCObjectWrapper(val `object`: VDJCObject) {
    val bestTarget by lazy { AirrUtil.bestTarget(`object`) }
    private var alignmentTarget = -2
    private var alignmentVDJ = false
    private var alignment: AirrAlignment? = null

    fun asClone(): Clone = `object` as Clone

    fun asAlignment(): VDJCAlignments = `object` as VDJCAlignments

    fun getAirrAlignment(target: Int, vdj: Boolean): AirrAlignment? {
        if (alignmentTarget == target && alignmentVDJ == vdj) return alignment
        alignmentTarget = target
        alignmentVDJ = vdj
        alignment = AirrUtil.calculateAirrAlignment(`object`, target, vdj)
        return alignment
    }
}
