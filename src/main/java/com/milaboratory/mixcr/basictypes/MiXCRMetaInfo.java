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
package com.milaboratory.mixcr.basictypes;

import com.milaboratory.mixcr.assembler.CloneAssemblerParameters;
import com.milaboratory.mixcr.basictypes.tag.TagsInfo;
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters;
import com.milaboratory.primitivio.annotations.Serializable;

import java.util.Objects;
import java.util.function.Function;

/**
 * This class represents common meta-information stored in the headers of vdjca/clna/clns files.
 * The information that is relevant for the downstream analysis.
 */
@Serializable(by = IO.MiXCRMetaInfoSerializer.class)
public final class MiXCRMetaInfo {
    /** Set by used on align step, used to deduce defaults on all downstream steps */
    public final String tagPreset;
    /** Aligner parameters */
    public final TagsInfo tagsInfo;
    /** Aligner parameters */
    public final VDJCAlignerParameters alignerParameters;
    /** Clone assembler parameters */
    public final CloneAssemblerParameters assemblerParameters;

    public MiXCRMetaInfo(String tagPreset, TagsInfo tagsInfo, VDJCAlignerParameters alignerParameters, CloneAssemblerParameters assemblerParameters) {
        this.tagPreset = tagPreset;
        this.tagsInfo = tagsInfo == null ? TagsInfo.NO_TAGS : tagsInfo;
        this.alignerParameters = Objects.requireNonNull(alignerParameters);
        this.assemblerParameters = assemblerParameters;
    }

    public MiXCRMetaInfo withTagInfo(TagsInfo tagsInfo) {
        return new MiXCRMetaInfo(tagPreset, tagsInfo, alignerParameters, assemblerParameters);
    }

    public MiXCRMetaInfo updateTagInfo(Function<TagsInfo, TagsInfo> tagsInfoUpdate) {
        return new MiXCRMetaInfo(tagPreset, tagsInfoUpdate.apply(tagsInfo), alignerParameters, assemblerParameters);
    }

    public MiXCRMetaInfo withAssemblerParameters(CloneAssemblerParameters assemblerParameters) {
        return new MiXCRMetaInfo(tagPreset, tagsInfo, alignerParameters, assemblerParameters);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MiXCRMetaInfo that = (MiXCRMetaInfo) o;
        return Objects.equals(tagPreset, that.tagPreset) && Objects.equals(tagsInfo, that.tagsInfo) && Objects.equals(alignerParameters, that.alignerParameters) && Objects.equals(assemblerParameters, that.assemblerParameters);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tagPreset, tagsInfo, alignerParameters, assemblerParameters);
    }
}
