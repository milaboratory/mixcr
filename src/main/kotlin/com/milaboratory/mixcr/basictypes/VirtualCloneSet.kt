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
package com.milaboratory.mixcr.basictypes

import com.milaboratory.mixcr.basictypes.tag.TagCount
import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.Serializer
import com.milaboratory.primitivio.annotations.Serializable
import com.milaboratory.primitivio.readIntArray
import com.milaboratory.primitivio.readObjectOptional
import com.milaboratory.primitivio.readObjectRequired
import com.milaboratory.primitivio.writeIntArray

@Serializable(by = VirtualCloneSet.SerializerImpl::class)
class VirtualCloneSet(
    override val totalCount: Double,
    override val totalTagCounts: TagCount?,
    override val header: MiXCRHeader,
    override val footer: MiXCRFooter,
    private val tagDiversity: IntArray
) : CloneSetInfo {
    override fun getTagDiversity(level: Int): Int = tagDiversity[level]

    class SerializerImpl : Serializer<VirtualCloneSet> {
        override fun write(output: PrimitivO, obj: VirtualCloneSet) {
            output.writeDouble(obj.totalCount)
            output.writeObject(obj.totalTagCounts)
            output.writeObject(obj.header)
            output.writeObject(obj.footer)
            output.writeIntArray(obj.tagDiversity)
        }

        override fun read(input: PrimitivI): VirtualCloneSet {
            val totalCount = input.readDouble()
            val totalTagCounts = input.readObjectOptional<TagCount>()
            val header = input.readObjectRequired<MiXCRHeader>()
            val footer = input.readObjectRequired<MiXCRFooter>()
            val tagDiversity = input.readIntArray()
            return VirtualCloneSet(
                totalCount,
                totalTagCounts,
                header,
                footer,
                tagDiversity
            )
        }
    }
}
