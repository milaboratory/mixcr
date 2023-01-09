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

/** Parent interface for all mixcr command params */
interface MiXCRParams {
    @get:JsonIgnore
    val command: AnyMiXCRCommand
}

/** Marks all mixcr params objects that may link external data (i.e. some parts of parameters may be defined as file
 * names from where the value should be loaded) */
interface PackableMiXCRParams<P : PackableMiXCRParams<P>> : MiXCRParams {
    /** Embeds all the parameters linking external information sources into the params object */
    fun pack(): P
}
