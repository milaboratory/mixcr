package com.milaboratory.mixcr.cli

import com.milaboratory.mixcr.trees.SHMTreesWriter
import picocli.CommandLine.Parameters

abstract class CommandExportShmTreesAbstract : ACommandWithOutputMiXCR() {
    @Parameters(
        arity = "2",
        description = ["trees.${SHMTreesWriter.shmFileExtension} output"]
    )
    open var inOut: List<String> = ArrayList()

    override fun getInputFiles(): List<String> = listOf(inOut.first())

    override fun getOutputFiles(): List<String> = listOf(inOut.last())

    protected val inputFile get() = inputFiles.first()
    protected val outputFile get() = outputFiles.first()

    override fun validate() {
        if (!inputFile.endsWith(".${SHMTreesWriter.shmFileExtension}")) {
            throwValidationException("Input file should have extension ${SHMTreesWriter.shmFileExtension}. Given $inputFile")
        }
    }
}