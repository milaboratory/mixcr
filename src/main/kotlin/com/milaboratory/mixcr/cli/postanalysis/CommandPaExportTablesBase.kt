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

import com.milaboratory.mixcr.cli.ValidationException
import picocli.CommandLine.Parameters
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension

abstract class CommandPaExportTablesBase : CommandPaExport {
    @Parameters(
        description = ["Path for output file."],
        index = "1",
        paramLabel = "table.(tsv|csv)"
    )
    lateinit var out: Path

    constructor()

    constructor(paResult: PaResult, out: Path) : super(paResult) {
        this.out = out
    }

    override fun validate() {
        ValidationException.requireExtension("Output file must have", out, "tsv", "csv")
    }

    protected fun outDir(): Path = out.toAbsolutePath().parent

    protected fun outPrefix(): String {
        val fName = out.fileName.toString()
        return fName.dropLast(4)
    }

    protected fun outExtension(group: IsolationGroup): String =
        group.extension() + "." + out.extension

    protected fun separator(): String =
        if (out.extension == "tsv") "\t" else ","

    override fun run(result: PaResultByGroup) {
        Files.createDirectories(outDir())
        run1(result)
    }

    abstract fun run1(result: PaResultByGroup)
}
