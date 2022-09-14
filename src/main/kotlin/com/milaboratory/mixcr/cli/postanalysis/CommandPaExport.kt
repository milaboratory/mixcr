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

import com.milaboratory.mixcr.cli.AbstractMiXCRCommand
import io.repseq.core.Chains
import picocli.CommandLine
import java.nio.file.Paths

/**
 *
 */
abstract class CommandPaExport : AbstractMiXCRCommand {
    @CommandLine.Parameters(
        description = ["Input file with postanalysis results."],
        index = "0",
        defaultValue = "pa.json.gz"
    )
    lateinit var `in`: String

    @CommandLine.Option(description = ["Export for specific chains only"], names = ["--chains"])
    var chains: List<String>? = null

    private val parsedPaResultFromInput: PaResult by lazy {
        PaResult.readJson(Paths.get(`in`).toAbsolutePath())
    }

    private val paResultFromConstructor: PaResult?

    constructor() {
        paResultFromConstructor = null
    }

    /** Constructor used to export tables from code  */
    internal constructor(paResult: PaResult) {
        this.paResultFromConstructor = paResult
    }

    override fun getInputFiles(): List<String> = listOf(`in`)

    /**
     * Get full PA result
     */
    protected fun getPaResult(): PaResult = paResultFromConstructor ?: parsedPaResultFromInput

    override fun run0() {
        val set = chains?.map { name: String -> Chains.getNamedChains(name) }?.toSet()
        for (r in getPaResult().results) {
            if (set == null || set.any { c -> c.chains.intersects(r.group.chains.chains) }) {
                run(r)
            }
        }
    }

    abstract fun run(result: PaResultByGroup)
}
