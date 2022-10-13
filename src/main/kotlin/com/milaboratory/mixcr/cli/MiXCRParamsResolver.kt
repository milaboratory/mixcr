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
package com.milaboratory.mixcr.cli

import com.milaboratory.cli.ParamsResolver
import com.milaboratory.cli.PresetAware
import com.milaboratory.mixcr.Flags
import com.milaboratory.mixcr.MiXCRCommandDescriptor
import com.milaboratory.mixcr.MiXCRMixin
import com.milaboratory.mixcr.MiXCRParamsBundle
import com.milaboratory.mixcr.Presets
import kotlin.reflect.KProperty1

abstract class MiXCRParamsResolver<P : Any>(
    paramsProperty: KProperty1<MiXCRParamsBundle, P?>
) : ParamsResolver<MiXCRParamsBundle, P>(Presets::resolveParamsBundle, paramsProperty) {
    override fun validateBundle(bundle: MiXCRParamsBundle) {
        if (bundle.flags.isNotEmpty()) {
            println("Preset errors: ")
            bundle.flags.forEach { flag ->
                println()
                println("- " + Flags.flagMessages[flag]!!.replace("\n", "\n  "))
            }
            println()

            throw ValidationException("Error validating preset bundle.");
        }
        if (
            bundle.pipeline?.steps?.contains(MiXCRCommandDescriptor.assembleContigs) == true &&
            bundle.assemble?.clnaOutput == false
        )
            throw ValidationException("assembleContigs step required clnaOutput=true on assemble step")
    }
}

interface MiXCRPresetAwareCommand<P : Any> : PresetAware<MiXCRParamsBundle, P>

interface MiXCRMixinCollection {
    val mixins: List<MiXCRMixin>

    companion object {
        fun combine(vararg list: MiXCRMixinCollection?) = object : MiXCRMixinCollection {
            override val mixins = list.filterNotNull().flatMap { it.mixins }
        }
    }
}

abstract class MiXCRMixinCollector : MiXCRMixinCollection {
    private val _mixins = mutableListOf<MiXCRMixin>()

    protected fun mixIn(mixin: MiXCRMixin) {
        _mixins += mixin
    }

    override val mixins: List<MiXCRMixin> get() = _mixins.sorted()
}
