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
import com.milaboratory.primitivio.readSet
import com.milaboratory.primitivio.writeCollection

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
    val features: Set<Feature> = emptySet()
) {
    fun withTagInfo(tagsInfo: TagsInfo): MiXCRMetaInfo =
        copy(tagsInfo = tagsInfo)

    fun updateTagInfo(tagsInfoUpdate: (TagsInfo) -> TagsInfo): MiXCRMetaInfo =
        copy(tagsInfo = tagsInfoUpdate(tagsInfo))

    fun withAssemblerParameters(assemblerParameters: CloneAssemblerParameters): MiXCRMetaInfo =
        copy(assemblerParameters = assemblerParameters)

    fun withFeature(feature: Feature) = copy(features = features + feature)
    fun withoutFeature(feature: Feature) = copy(features = features - feature)

    class SerializerImpl : BasicSerializer<MiXCRMetaInfo>() {
        override fun write(output: PrimitivO, obj: MiXCRMetaInfo) {
            output.writeObject(obj.tagPreset)
            output.writeObject(obj.tagsInfo)
            output.writeObject(obj.alignerParameters)
            output.writeObject(obj.assemblerParameters)
            output.writeCollection(obj.features)
        }

        override fun read(input: PrimitivI): MiXCRMetaInfo = MiXCRMetaInfo(
            input.readObjectOptional(),
            input.readObjectRequired(),
            input.readObjectRequired(),
            input.readObjectOptional(),
            input.readSet()
        )
    }

    @Serializable(by = Feature.SerializerImpl::class)
    sealed interface Feature {
        object AllelesFound : Feature

        object AllClonesAlignedByAssembleFeatures : Feature

        /**
         * For forward capability
         */
        data class Unknown(
            val toSerialize: String
        ) : Feature

        class SerializerImpl : BasicSerializer<Feature>() {
            override fun write(output: PrimitivO, obj: Feature) {
                output.writeObject(
                    when (obj) {
                        AllClonesAlignedByAssembleFeatures -> "AllClonesAlignedByAssembleFeatures"
                        AllelesFound -> "AllelesFound"
                        is Unknown -> obj.toSerialize
                    }
                )
            }

            override fun read(input: PrimitivI): Feature =
                when (val source = input.readObject(String::class.java)) {
                    "AllClonesAlignedByAssembleFeatures" -> AllClonesAlignedByAssembleFeatures
                    "AllelesFound" -> AllelesFound
                    else -> Unknown(source)
                }
        }
    }
}
