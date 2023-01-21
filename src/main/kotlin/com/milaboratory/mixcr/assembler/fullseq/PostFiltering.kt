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
package com.milaboratory.mixcr.assembler.fullseq

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.milaboratory.mixcr.basictypes.Clone
import com.milaboratory.mixcr.basictypes.GeneFeatures


/** Post filtering clonotype predicate */
fun interface PostFilteringFunc {
    fun accept(clone: Clone): Boolean
}

/** Filtering to clonotypes after contig assembly */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
sealed interface PostFiltering {
    /** Returns clone predicate, based on other parameters of full seq assembler */
    fun getFilter(allParameters: FullSeqAssemblerParameters): PostFilteringFunc

    /** Don't filter output clonotypes */
    @JsonTypeName("NoFiltering")
    object NoFiltering : PostFiltering {
        override fun getFilter(allParameters: FullSeqAssemblerParameters) = PostFilteringFunc { true }
    }

    /** Only clonotypes completely covering [.assemblingRegions] will be retained. */
    @JsonTypeName("OnlyFullyAssembled")
    @Deprecated("Use OnlyCovering")
    object OnlyFullyAssembled : PostFiltering {
        override fun getFilter(allParameters: FullSeqAssemblerParameters) = run {
            val features = allParameters.assemblingRegions!!.features
            PostFilteringFunc { clone -> features.all { clone.isAvailable(it) } }
        }
    }

    /** Only clonotypes completely covering [.assemblingRegions] and having no "N" letters will be retained. */
    @JsonTypeName("OnlyFullyDefined")
    @Deprecated("Use OnlyUnambiguouslyCovering")
    object OnlyFullyDefined : PostFiltering {
        override fun getFilter(allParameters: FullSeqAssemblerParameters) = run {
            val features = allParameters.assemblingRegions!!.features
            PostFilteringFunc { clone -> features.all { clone.getFeature(it)?.sequence?.containsWildcards() == false } }
        }
    }

    /** Filter clonotypes based on the total length of assembled contig */
    @JsonTypeName("MinimalContigLength")
    data class MinimalContigLength(val minimalLength: Int) : PostFiltering {
        override fun getFilter(allParameters: FullSeqAssemblerParameters) =
            PostFilteringFunc { clone ->
                clone.targets.sumOf { it.size() } >= minimalLength
            }
    }

    /** Filter clonotypes that covers a certain gene feature */
    @JsonTypeName("OnlyCovering")
    data class OnlyCovering(val geneFeatures: GeneFeatures) : PostFiltering {
        override fun getFilter(allParameters: FullSeqAssemblerParameters) =
            PostFilteringFunc { clone -> geneFeatures.features.all { clone.isAvailable(it) } }
    }

    /** Filter clonotypes that covers a certain gene feature and have no wildcards in the region */
    @JsonTypeName("OnlyUnambiguouslyCovering")
    data class OnlyUnambiguouslyCovering(val geneFeatures: GeneFeatures) : PostFiltering {
        override fun getFilter(allParameters: FullSeqAssemblerParameters) =
            PostFilteringFunc { clone ->
                geneFeatures.features.all { clone.getFeature(it)?.sequence?.containsWildcards() == false }
            }
    }
}