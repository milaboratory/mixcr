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
package com.milaboratory.mixcr.util

import java.io.File
import java.io.PrintStream
import java.nio.file.Files
import java.util.*
import java.util.stream.Collectors

object XSV {
    @JvmStatic
    fun writeXSVHeaders(output: PrintStream, columns: Collection<String>, delimiter: String) {
        output.println(java.lang.String.join(delimiter, columns))
    }

    @JvmStatic
    fun <T> writeXSVBody(
        output: PrintStream,
        records: Collection<T>,
        columns: Map<String, (T) -> Any?>,
        delimiter: String
    ) {
        for (record in records) {
            output.println(columns.values.stream()
                .map { column -> Objects.toString(column(record), "") }
                .collect(Collectors.joining(delimiter))
            )
        }
    }

    fun readXSV(input: File, columns: Collection<String>, delimiter: String): List<Map<String, String?>> {
        val lines = Files.readAllLines(input.toPath())
        require(lines.size != 0) { "no header row" }
        if (lines.size == 1) {
            return emptyList()
        }
        val header = lines[0]
        val columnsPositions: MutableMap<String, Int> = HashMap()
        val columnsFromFile = header.split(delimiter.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
        for (i in columnsFromFile.indices) {
            columnsPositions[columnsFromFile[i]] = i
        }
        for (column in columns) {
            require(columnsPositions.containsKey(column)) { "no column with name $column" }
        }
        return lines.subList(1, lines.size - 1).stream()
            .map { row: String ->
                val cells = row.split(delimiter.toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val result: MutableMap<String, String?> = HashMap()
                for (column in columns) {
                    var cellValue: String?
                    if (cells.size <= columnsPositions[column]!!) {
                        cellValue = null
                    } else {
                        cellValue = cells[columnsPositions[column]!!]
                        if (cellValue == "") {
                            cellValue = null
                        }
                    }
                    result[column] = cellValue
                }
                result
            }
            .collect(Collectors.toList())
    }
}
