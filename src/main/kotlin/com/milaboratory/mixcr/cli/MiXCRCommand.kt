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

import com.milaboratory.cli.*
import com.milaboratory.mixcr.MiXCRParamsBundle
import kotlin.reflect.KProperty1

interface MiXCRCommandI {
    fun throwValidationExceptionKotlin(message: String, printHelp: Boolean): Nothing
    fun throwValidationExceptionKotlin(message: String): Nothing
    fun throwExecutionExceptionKotlin(message: String): Nothing
}

abstract class MiXCRCommand : ACommand("mixcr"), MiXCRCommandI {
    override fun throwValidationExceptionKotlin(message: String, printHelp: Boolean): Nothing {
        super.throwValidationException(message, printHelp)
        error(message)
    }

    override fun throwValidationExceptionKotlin(message: String): Nothing {
        super.throwValidationException(message)
        error(message)
    }

    override fun throwExecutionExceptionKotlin(message: String): Nothing {
        super.throwExecutionException(message)
        error(message)
    }
}

abstract class MiXCRParamsResolver<P : Any>(paramsProperty: KProperty1<MiXCRParamsBundle, P?>) :
    ParamsResolver<MiXCRParamsBundle, P>(paramsProperty)

abstract class MiXCRPresetAwareCommand<P : Any> : MiXCRCommand(), PresetAware<MiXCRParamsBundle, P>

class MiXCRParamsBundleMixIn(val priority: Int, val mutation: POverride<MiXCRParamsBundle>)

@POverridesBuilderDsl
interface MixInBuilderOps : POverridesBuilderOps<MiXCRParamsBundle> {
    fun setPriority(priority: Int)

    fun dropFlag(flagName: String) = MiXCRParamsBundle::flags.updateBy { it - flagName }
}

typealias MixInBuilderFunc = MixInBuilderOps.() -> Unit

interface MiXCRMixInSet {
    fun mixIn(action: MixInBuilderFunc)
}

abstract class MiXCRMixInCollector : MiXCRMixInSet {
    private val mixins = mutableListOf<MiXCRParamsBundleMixIn>()

    override fun mixIn(action: MixInBuilderFunc) {
        val overrides = mutableListOf<POverride<MiXCRParamsBundle>>()
        var priorityValue = 0
        val builderTarget = object : POverridesBuilderOpsAbstract<MiXCRParamsBundle>(), MixInBuilderOps {
            override fun addOverride(override: POverride<MiXCRParamsBundle>) {
                overrides += override
            }

            override fun setPriority(priority: Int) {
                priorityValue = priority
            }
        }
        builderTarget.action()
        mixins += MiXCRParamsBundleMixIn(priorityValue) { overrides.fold(it) { acc, o -> o.apply(acc) } }
    }

    val bundleOverride
        get() = POverride<MiXCRParamsBundle> { bundle ->
            val mixinsCopy = ArrayList(mixins)
            mixinsCopy.sortBy { -it.priority }
            mixinsCopy.fold(bundle) { b, m -> m.mutation.apply(b) }
        }
}