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
import picocli.CommandLine
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

abstract class CommandPaExportTablesBase : CommandPaExport {
    @CommandLine.Parameters(description = ["Path for output files"], index = "1", defaultValue = "path/table.tsv")
    lateinit var out: String
    override val outputFiles: List<String>
        get() {
            return emptyList() // output will be always overriden
        }

    constructor()

    constructor(paResult: PaResult, out: String) : super(paResult) {
        this.out = out
    }

    override fun validate() {
        super.validate()
        if (!out.endsWith(".tsv") && !out.endsWith(".csv"))
            throw ValidationException("Output file must have .tsv or .csv extension")
    }

    protected fun outDir(): Path = Paths.get(out).toAbsolutePath().parent

    protected fun outPrefix(): String {
        val fName = Paths.get(out).fileName.toString()
        return fName.dropLast(4)
    }

    protected fun outExtension(group: IsolationGroup): String =
        group.extension() + "." + out.takeLast(3)

    protected fun separator(): String =
        if (out.endsWith("tsv")) "\t" else ","

    override fun run(result: PaResultByGroup) {
        Files.createDirectories(outDir())
        run1(result)
    }

    abstract fun run1(result: PaResultByGroup)
}
