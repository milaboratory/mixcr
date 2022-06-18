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
package com.milaboratory.mixcr.export

import com.milaboratory.mixcr.basictypes.VDJCFileHeaderData
import com.milaboratory.mixcr.cli.CommandExport.FieldData
import java.util.*

abstract class BaseFieldExtractors {
    private val fields: Array<Field<out Any>> by lazy {
        initFields()
    }

    protected abstract fun initFields(): Array<Field<out Any>>

    //copy of com.milaboratory.mixcr.cli.CommandExport.extractor
    fun <E> extract(
        fd: FieldData,
        clazz: Class<E>,
        header: VDJCFileHeaderData,
        m: OutputMode
    ): List<FieldExtractor<E>> {
        for (f in fields) {
            if (fd.field.equals(f.command, ignoreCase = true) && f.canExtractFrom(clazz)) {
                @Suppress("UNCHECKED_CAST")
                f as Field<E>
                return if (f.nArguments() == 0) {
                    if (!(fd.args.isEmpty() ||
                            fd.args.size == 1 && (fd.args[0].equals("true", ignoreCase = true)
                            || fd.args[0].equals("false", ignoreCase = true)))
                    ) throw RuntimeException()
                    listOf<FieldExtractor<E>>(f.create(m, header, emptyArray()))
                } else {
                    var i = 0
                    val extractors = ArrayList<FieldExtractor<E>>()
                    while (i < fd.args.size) {
                        extractors.add(f.create(m, header, Arrays.copyOfRange(fd.args, i, i + f.nArguments())))
                        i += f.nArguments()
                    }
                    extractors
                }
            }
        }
        throw IllegalArgumentException("illegal field: " + fd.field)
    }
}

