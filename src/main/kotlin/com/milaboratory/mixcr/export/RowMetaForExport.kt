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

import com.milaboratory.mixcr.basictypes.GeneFeatures
import com.milaboratory.mixcr.basictypes.MiXCRHeader
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.mixcr.basictypes.tag.TagsInfo

class RowMetaForExport(
    val tagsInfo: TagsInfo,
    val header: HeaderForExport
)

class HeaderForExport(
    val allTagsInfo: List<TagsInfo>,
    val allFullyCoveredBy: GeneFeatures?
) {
    constructor(origin: MiXCRHeader) : this(listOf(origin.tagsInfo), origin.allFullyCoveredBy)

    fun tagNamesWithType(tagType: TagType) = allTagsInfo.flatten()
        .filter { it.type == tagType }
        .map { it.name }
        .distinct()
}
