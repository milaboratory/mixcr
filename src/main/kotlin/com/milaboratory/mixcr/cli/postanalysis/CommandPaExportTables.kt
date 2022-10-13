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
package com.milaboratory.mixcr.cli.postanalysis

import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroupResult
import picocli.CommandLine.Command
import java.nio.file.Path

@Command(
    description = ["CD3 metrics, Diversity, V/J/VJ-Usage, CDR3/V-Spectratype, Overlap"]
)
class CommandPaExportTables : CommandPaExportTablesBase {
    constructor()
    constructor(paResult: PaResult, out: Path) : super(paResult, out)

    override fun run1(result: PaResultByGroup) {
        for (table in result.schema.tables) {
            writeTables(outExtension(result.group), result.result.getTable(table))
        }
    }

    private fun <K> writeTables(extension: String, tableResult: CharacteristicGroupResult<K>) {
        for (view in tableResult.group.views) {
            for (table in view.getTables(tableResult).values) {
                table.writeCSV(outDir(), "sample", outPrefix() + ".", separator(), extension)
            }
        }
    }
}
