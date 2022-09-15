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
import io.repseq.core.VDJCLibraryId
import io.repseq.dto.VDJCLibraryData

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
    val foundAlleles: FoundAlleles?,
    /** If all clones cut by the same feature and cover this feature fully */
    val allFullyCoveredBy: GeneFeatures?
) {
    fun withTagInfo(tagsInfo: TagsInfo): MiXCRMetaInfo =
        copy(tagsInfo = tagsInfo)

    fun updateTagInfo(tagsInfoUpdate: (TagsInfo) -> TagsInfo): MiXCRMetaInfo =
        copy(tagsInfo = tagsInfoUpdate(tagsInfo))

    fun withAssemblerParameters(assemblerParameters: CloneAssemblerParameters): MiXCRMetaInfo =
        copy(assemblerParameters = assemblerParameters)

    fun withAllClonesCutBy(allClonesAlignedBy: Array<GeneFeature>) =
        copy(allFullyCoveredBy = GeneFeatures(allClonesAlignedBy))

    @Serializable(by = FoundAlleles.SerializerImpl::class)
    data class FoundAlleles(
        val libraryName: String,
        val libraryData: VDJCLibraryData
    ) {
        val libraryIdWithoutChecksum: VDJCLibraryId get() = VDJCLibraryId(libraryName, libraryData.taxonId)

        class SerializerImpl : BasicSerializer<FoundAlleles>() {
            override fun write(output: PrimitivO, obj: FoundAlleles) {
                output.writeObject(obj.libraryName)
                output.writeObject(obj.libraryData)
            }

            override fun read(input: PrimitivI): FoundAlleles {
                val libraryName = input.readObjectRequired<String>()
                val libraryData = input.readObjectRequired<VDJCLibraryData>()
                return FoundAlleles(
                    libraryName,
                    libraryData
                )
            }

            override fun isReference(): Boolean = true
        }
    }

    class SerializerImpl : BasicSerializer<MiXCRMetaInfo>() {
        override fun write(output: PrimitivO, obj: MiXCRMetaInfo) {
            output.writeObject(obj.tagPreset)
            output.writeObject(obj.tagsInfo)
            output.writeObject(obj.alignerParameters)
            output.writeObject(obj.assemblerParameters)
            output.writeObject(obj.foundAlleles)
            output.writeObject(obj.allFullyCoveredBy)
        }

        override fun read(input: PrimitivI): MiXCRMetaInfo {
            val tagPreset = input.readObjectOptional<String>()
            val tagsInfo = input.readObjectRequired<TagsInfo>()
            val alignerParameters = input.readObjectRequired<VDJCAlignerParameters>()
            val assemblerParameters = input.readObjectOptional<CloneAssemblerParameters>()
            val foundAlleles = input.readObjectOptional<FoundAlleles>()
            val allFullyCoveredBy = input.readObjectOptional<GeneFeatures>()
            return MiXCRMetaInfo(
                tagPreset,
                tagsInfo,
                alignerParameters,
                assemblerParameters,
                foundAlleles,
                allFullyCoveredBy
            )
        }
    }
}
