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
package com.milaboratory.mixcr.cli

import com.milaboratory.app.ValidationException
import com.milaboratory.util.TempFileDest
import com.milaboratory.util.TempFileManager
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import picocli.CommandLine
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths

class CommandFindAllelesTest {
    private val temp: TempFileDest = TempFileManager.systemTempFolderDestination("test")
    private val file1: Path = temp.resolvePath("folder1/file1.clns")
    private val file2: Path = temp.resolvePath("folder2/file2.clns")
    private val file3: Path = temp.resolvePath("folder3/file1.clns")

    @Before
    @Throws(IOException::class)
    fun prepareFiles() {
        file1.toFile().parentFile.mkdirs()
        file1.toFile().createNewFile()
        file2.toFile().parentFile.mkdirs()
        file2.toFile().createNewFile()
        file3.toFile().parentFile.mkdirs()
        file3.toFile().createNewFile()
    }

    @Test
    fun outputsWrittenToCommonDirectory() {
        val p = Main.parseArgs(
            CommandFindAlleles.COMMAND_NAME,
            "--output-template", "/output/folder/{file_name}_with_alleles.clns",
            file1.toString(),
            file2.toString()
        ).parseResult
        val command = p.asCommandLineList()[p.asCommandLineList().size - 1].getCommand<CommandFindAlleles>()
        command.outputFiles shouldContainExactlyInAnyOrder listOf(
            Paths.get("/output/folder/file1_with_alleles.clns"),
            Paths.get("/output/folder/file2_with_alleles.clns")
        )
    }

    @Test
    fun outputsWrittenToOriginalDirectory() {
        val p = Main.parseArgs(
            CommandFindAlleles.COMMAND_NAME,
            "--output-template", "{file_dir_path}/{file_name}_with_alleles.clns",
            file1.toString(),
            file2.toString()
        ).parseResult
        val command = p.asCommandLineList()[p.asCommandLineList().size - 1].getCommand<CommandFindAlleles>()
        Assert.assertTrue(
            command.outputFiles[0].toString(),
            command.outputFiles[0].toString().endsWith("/folder1/file1_with_alleles.clns")
        )
        Assert.assertTrue(
            command.outputFiles[1].toString(),
            command.outputFiles[1].toString().endsWith("/folder2/file2_with_alleles.clns")
        )
    }

    @Test
    fun includeLibraryIntoOutputs() {
        val p = Main.parseArgs(
            CommandFindAlleles.COMMAND_NAME,
            "--export-library", "/output/folder/library.json",
            "--output-template", "/output/folder/{file_name}_with_alleles.clns",
            file1.toString(),
            file2.toString()
        ).parseResult
        val command = p.asCommandLineList()[p.asCommandLineList().size - 1].getCommand<CommandFindAlleles>()
        command.outputFiles shouldContainExactlyInAnyOrder listOf(
            Paths.get("/output/folder/file1_with_alleles.clns"),
            Paths.get("/output/folder/file2_with_alleles.clns"),
            Paths.get("/output/folder/library.json")
        )
    }

    @Test
    fun libraryMustBeJson() {
        try {
            val p = Main.parseArgs(
                CommandFindAlleles.COMMAND_NAME,
                "--export-library", "/output/folder/library.txt",
                "--output-template", "/output/folder/{file_name}_with_alleles.clns",
                file1.toString(),
                file2.toString()
            ).parseResult
            val command = p.asCommandLineList()[p.asCommandLineList().size - 1].getCommand<CommandFindAlleles>()
            command.validate()
            Assert.fail()
        } catch (e: ValidationException) {
            Assert.assertEquals("Require one of json, fasta file types, got /output/folder/library.txt", e.message)
        }
    }

    @Test
    fun outputsMustBeUniq() {
        val p = Main.parseArgs(
            CommandFindAlleles.COMMAND_NAME,
            "--output-template", "/output/folder/{file_name}.clns",
            file1.toString(),
            file3.toString()
        ).parseResult
        val command = p.asCommandLineList()[p.asCommandLineList().size - 1].getCommand<CommandFindAlleles>()
        try {
            command.outputFiles
            Assert.fail()
        } catch (e: ValidationException) {
            Assert.assertTrue(e.message, e.message.startsWith("Output clns files are not uniq:"))
        }
    }

    @Test
    fun templateMustBeClns() {
        val p = Main.parseArgs(
            CommandFindAlleles.COMMAND_NAME,
            "--output-template", "/output/folder/{file_name}.clna",
            file1.toString(),
            file2.toString()
        ).parseResult
        val command = p.asCommandLineList()[p.asCommandLineList().size - 1].getCommand<CommandFindAlleles>()
        try {
            command.outputFiles
            Assert.fail()
        } catch (e: ValidationException) {
            Assert.assertEquals(
                "Wrong template: command produces only clns, got /output/folder/{file_name}.clna",
                e.message
            )
        }
    }

    @Test
    fun shouldFailIfNoOutputTemplate() {
        try {
            Main.parseArgs(
                CommandFindAlleles.COMMAND_NAME,
                "--export-library", "/output/folder/library.json",
                file1.toString(),
                file2.toString()
            ).parseResult
            Assert.fail()
        } catch (e: CommandLine.ParameterException) {
            Assert.assertEquals(
                "Error: Missing required argument (specify one of these): (--output-template <template.clns> | --no-clns-output)",
                e.message
            )
        }
    }

    @Test
    fun shouldNotFailIfSpecifiedNoClnsOutput() {
        val p = Main.parseArgs(
            CommandFindAlleles.COMMAND_NAME,
            "--no-clns-output",
            "--export-library", "/output/folder/library.json",
            file1.toString(),
            file2.toString()
        ).parseResult
        val command = p.asCommandLineList()[p.asCommandLineList().size - 1].getCommand<CommandFindAlleles>()
        command.validate()
    }
}
