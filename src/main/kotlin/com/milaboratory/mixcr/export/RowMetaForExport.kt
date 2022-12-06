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

import com.milaboratory.mixcr.MiXCRStepReports
import com.milaboratory.mixcr.basictypes.GeneFeatures
import com.milaboratory.mixcr.basictypes.MiXCRFileInfo
import com.milaboratory.mixcr.basictypes.tag.TagType
import com.milaboratory.mixcr.basictypes.tag.TagsInfo

class RowMetaForExport(
    val tagsInfo: TagsInfo,
    val header: MetaForExport,
    val notCoveredAsEmpty: Boolean
) {
    val noTagText: String get() = if (notCoveredAsEmpty) "" else "no_tag"
    val notCoveredRegionText: String get() = if (notCoveredAsEmpty) "" else "region_not_covered"
    val notCoveredRegionTextForMutations: String get() = if (notCoveredAsEmpty) "-" else "region_not_covered"
}

class MetaForExport(
    val allTagsInfo: List<TagsInfo>,
    val allFullyCoveredBy: GeneFeatures?,
    val allReports: MiXCRStepReports
) {
    constructor(origin: MiXCRFileInfo) : this(
        listOf(origin.header.tagsInfo),
        origin.header.allFullyCoveredBy,
        origin.footer.reports
    )

    fun tagNamesWithType(tagType: TagType) = allTagsInfo.flatten()
        .filter { it.type == tagType }
        .map { it.name }
        .distinct()
}
