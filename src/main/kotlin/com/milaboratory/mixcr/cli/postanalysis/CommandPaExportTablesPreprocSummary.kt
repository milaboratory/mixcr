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

import com.milaboratory.mixcr.postanalysis.SetPreprocessorSummary
import picocli.CommandLine

@CommandLine.Command(
    name = "exportPreprocTables",
    sortOptions = false,
    separator = " ",
    description = ["Export preprocessing summary tables."]
)
class CommandPaExportTablesPreprocSummary : CommandPaExportTablesBase {
    constructor()
    constructor(paResult: PaResult, out: String) : super(paResult, out) {}

    override fun run1(result: PaResultByGroup) {
        SetPreprocessorSummary.byCharToCSV(
            outDir().resolve(outPrefix() + outExtension(result.group)),
            result.schema,
            result.result,
            separator()
        )
    }
}
