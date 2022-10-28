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

import cc.redberry.pipe.InputPort
import org.apache.commons.io.output.CloseShieldOutputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path

class InfoWriter<T : Any> private constructor(
    private val fieldExtractors: List<FieldExtractor<T>>,
    private val outputStream: OutputStream
) : InputPort<T>, AutoCloseable {

    private fun printHeader() {
        for (i in fieldExtractors.indices) {
            outputStream.write(fieldExtractors[i].header.toByteArray())
            if (i == fieldExtractors.size - 1) break
            outputStream.write('\t'.code)
        }
        outputStream.write('\n'.code)
    }

    override fun put(t: T) {
        for (i in fieldExtractors.indices) {
            outputStream.write(fieldExtractors[i].extractValue(t).toByteArray())
            if (i == fieldExtractors.size - 1) break
            outputStream.write('\t'.code)
        }
        outputStream.write('\n'.code)
    }

    override fun close() {
        outputStream.close()
        for (fe in fieldExtractors) if (fe is Closeable) (fe as Closeable).close()
    }

    companion object {
        fun <T : Any> create(
            file: Path?,
            fieldExtractors: List<FieldExtractor<T>>,
            printHeader: Boolean
        ): InfoWriter<T> {
            val outputStream = when {
                file != null -> BufferedOutputStream(Files.newOutputStream(file), 65536)
                else -> CloseShieldOutputStream.wrap(System.out)
            }
            val result = InfoWriter(fieldExtractors, outputStream)
            if (printHeader)
                result.printHeader()
            return result
        }
    }
}
