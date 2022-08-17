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

import com.milaboratory.mitool.pattern.search.BasicSerializer
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters
import com.milaboratory.mixcr.basictypes.tag.TagsInfo
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import com.milaboratory.primitivio.PrimitivI
import com.milaboratory.primitivio.PrimitivO
import com.milaboratory.primitivio.annotations.Serializable
import com.milaboratory.primitivio.readObjectOptional
import com.milaboratory.primitivio.readObjectRequired
import io.repseq.core.GeneFeature
import io.repseq.core.VDJCLibrary

/**
 * This class represents common meta-information stored in the headers of vdjca/clna/clns files.
 * The information that is relevant for the downstream analysis.
 */
@Serializable(by = MiXCRMetaInfo.SerializerImpl::class)
data class MiXCRMetaInfo(
    /** Set by used on align step, used to deduce defaults on all downstream steps  */
    val tagPreset: String? = null,
    /** Aligner parameters */
    val tagsInfo: TagsInfo = TagsInfo.NO_TAGS,
    /** Aligner parameters */
    val alignerParameters: VDJCAlignerParameters,
    /** Clone assembler parameters  */
    val assemblerParameters: CloneAssemblerParameters? = null,
    /** Library produced by search of alleles */
    val foundAlleles: VDJCLibrary?,
    /** If all clones cut by the same feature and cover this feature fully */
    val allClonesCutBy: GeneFeature?
) {
    fun withTagInfo(tagsInfo: TagsInfo): MiXCRMetaInfo =
        copy(tagsInfo = tagsInfo)

    fun updateTagInfo(tagsInfoUpdate: (TagsInfo) -> TagsInfo): MiXCRMetaInfo =
        copy(tagsInfo = tagsInfoUpdate(tagsInfo))

    fun withAssemblerParameters(assemblerParameters: CloneAssemblerParameters): MiXCRMetaInfo =
        copy(assemblerParameters = assemblerParameters)

    fun withAllClonesCutBy(allClonesAlignedBy: GeneFeature) = copy(allClonesCutBy = allClonesAlignedBy)

    class SerializerImpl : BasicSerializer<MiXCRMetaInfo>() {
        override fun write(output: PrimitivO, obj: MiXCRMetaInfo) {
            output.writeObject(obj.tagPreset)
            output.writeObject(obj.tagsInfo)
            output.writeObject(obj.alignerParameters)
            output.writeObject(obj.assemblerParameters)
            output.writeObject(obj.foundAlleles)
            output.writeObject(obj.allClonesCutBy)
        }

        override fun read(input: PrimitivI): MiXCRMetaInfo {
            return MiXCRMetaInfo(
                input.readObjectOptional(),
                input.readObjectRequired(),
                input.readObjectRequired(),
                input.readObjectOptional(),
                input.readObjectOptional(),
                input.readObjectOptional()
            )
        }
    }
}
