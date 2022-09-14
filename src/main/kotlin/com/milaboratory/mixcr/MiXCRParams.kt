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
package com.milaboratory.mixcr

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.milaboratory.mixcr.cli.*

// @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, )
interface MiXCRParams {
    @get:JsonIgnore
    val command: MiXCRCommand<*>
}

sealed interface MiXCRCommand<T : MiXCRParams> {
    fun extractFromBundle(bundle: MiXCRParamsBundle): T?

    object align : MiXCRCommand<CommandAlign.Params> {
        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.align
    }

    object exportAlignments : MiXCRCommand<CommandExportAlignments.Params> {
        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.exportAlignments
    }

    object extend : MiXCRCommand<CommandExtend.Params> {
        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.extend
    }

    object assemblePartial : MiXCRCommand<CommandAssemblePartial.Params> {
        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.assemblePartial
    }

    object assemble : MiXCRCommand<CommandAssemble.Params> {
        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.assemble
    }

    object assembleContigs : MiXCRCommand<CommandAssembleContigs.Params> {
        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.assembleContigs
    }

    object exportClones : MiXCRCommand<CommandExportClones.Params> {
        override fun extractFromBundle(bundle: MiXCRParamsBundle) = bundle.exportClones
    }
}

