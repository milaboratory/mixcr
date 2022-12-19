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

import com.milaboratory.app.logger
import com.milaboratory.mixcr.basictypes.tag.TagType

object TagsUtil {
    fun FieldsCollection<*>.checkTagExists(header: MetaForExport, tagName: String) {
        if (header.allTagsInfo.none { it[tagName] != null }) {
            logger.warn("No tag $tagName in data ($cmdArgName $tagName)")
        }
    }

    fun FieldsCollection<*>.checkTagTypeExists(header: MetaForExport, tagType: TagType) {
        if (header.allTagsInfo.none { it.hasTagsWithType(tagType) }) {
            logger.warn("No tags with type $tagType in data ($cmdArgName $tagType)")
        }
    }
}
