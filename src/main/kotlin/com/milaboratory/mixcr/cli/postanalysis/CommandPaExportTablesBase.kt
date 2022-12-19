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

import com.milaboratory.app.InputFileType
import com.milaboratory.app.ValidationException
import com.milaboratory.mixcr.postanalysis.SetPreprocessorSummary
import com.milaboratory.mixcr.postanalysis.ui.CharacteristicGroupResult
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

abstract class CommandPaExportTablesBase : CommandPaExport() {
    @Parameters(
        description = ["Path for output file."],
        index = "1",
        paramLabel = "table.(tsv|csv)"
    )
    lateinit var out: Path

    override fun validate() {
        super.validate()
        ValidationException.requireFileType(out, InputFileType.XSV)
    }

    abstract class Executor {
        fun run(result: PaResultByGroup, out: Path) {
            val dir = out.toAbsolutePath().parent
            Files.createDirectories(dir)
            run0(
                result,
                OutputDescription(
                    dir,
                    out.fileName.toString().dropLast(4),
                    if (out.extension == "tsv") "\t" else ","
                ) { group -> group.extension() + "." + out.extension }
            )
        }

        protected abstract fun run0(result: PaResultByGroup, out: OutputDescription)

        protected data class OutputDescription(
            val dir: Path,
            val prefix: String,
            val separator: String,
            val extension: (group: IsolationGroup) -> String
        )
    }

    @Command(
        description = ["CD3 metrics, Diversity, V/J/VJ-Usage, CDR3/V-Spectratype, Overlap"]
    )
    class Tables : CommandPaExportTablesBase() {
        override fun run(result: PaResultByGroup) {
            executor.run(result, out)
        }

        companion object {
            val executor = object : Executor() {
                override fun run0(result: PaResultByGroup, out: OutputDescription) {
                    for (table in result.schema.tables) {
                        writeTables(out.extension(result.group), result.result.getTable(table), out)
                    }
                }

                private fun <K> writeTables(
                    extension: String,
                    tableResult: CharacteristicGroupResult<K>,
                    out: OutputDescription
                ) {
                    for (view in tableResult.group.views) {
                        for (table in view.getTables(tableResult).values) {
                            table.writeCSV(out.dir, "sample", out.prefix + ".", out.separator, extension)
                        }
                    }
                }
            }
        }
    }

    @Command(
        description = ["Export preprocessing summary tables."]
    )
    class PreprocSummary : CommandPaExportTablesBase() {
        override fun run(result: PaResultByGroup) {
            executor.run(result, out)
        }

        companion object {
            val executor = object : Executor() {
                override fun run0(result: PaResultByGroup, out: OutputDescription) {
                    SetPreprocessorSummary.byCharToCSV(
                        out.dir.resolve(out.prefix + out.extension(result.group)),
                        result.schema,
                        result.result,
                        out.separator
                    )
                }
            }
        }
    }
}
