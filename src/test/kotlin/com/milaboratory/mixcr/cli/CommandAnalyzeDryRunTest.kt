/*
 * Copyright (c) 2014-2024, MiLaboratories Inc. All Rights Reserved
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

import com.milaboratory.mixcr.basictypes.CloneSetIO
import com.milaboratory.util.TempFileManager
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

class CommandAnalyzeDryRunTest {
    private val r1 = resource("umi_ig_data_2_subset_R1.fastq.gz")
    private val r2 = resource("umi_ig_data_2_subset_R2.fastq.gz")

    @Test
    fun `MiTool preset is planned to the end with every consensus round`() {
        val dir = TempFileManager.newTempDir().toPath()
        val steps = dryRun(dir.resolve("sample"), "10x-sc-xcr-vdj", "--species", "hs")

        val commands = steps.map { it[1] + if (it[1] == "mitool") " " + it[2] else "" }
        commands.distinct().take(4) shouldContainExactly
                listOf("mitool parse", "mitool refine-tags", "mitool consensus", "align")
        withClue("several consensus rounds") { (commands.count { it == "mitool consensus" } > 1) shouldBe true }

        // Production steps read what the previous one wrote
        val production = steps.filterNot { it[1].startsWith("export") || it[1] == "qc" }
        production.zipWithNext().forEach { (previous, next) ->
            withClue(next.joinToString(" ")) { next[next.size - 2] shouldBe previous.last() }
        }

        val preset = steps.first().windowed(2).single { it[0] == "--preset" }[1].removePrefix("local:")
        Paths.get("$preset.yaml").exists() shouldBe true
    }

    @Test
    fun `printed steps run one by one match analyze`() {
        val root = TempFileManager.newTempDir().toPath()
        val analyzeDir = root.resolve("analyze").also { it.createDirectories() }
        val planDir = root.resolve("plan").also { it.createDirectories() }
        val presetArgs = listOf("10x-sc-xcr-vdj", "--species", "hs")

        run(*(listOf("analyze") + presetArgs + listOf(r1, r2, analyzeDir.resolve("sample")).map { it.toString() })
            .toTypedArray())
        dryRun(planDir.resolve("sample"), *presetArgs.toTypedArray()).forEach { step ->
            withClue(step.joinToString(" ")) { run(*step.drop(1).toTypedArray()) }
        }

        val analyzeClns = analyzeDir.filesWithExtension("clns") + analyzeDir.filesWithExtension("clna")
        analyzeClns.shouldNotBeEmpty()
        planDir.filesWithExtension("clns") + planDir.filesWithExtension("clna") shouldBe analyzeClns
        analyzeClns.forEach { name ->
            withClue(name) {
                CloneSetIO.read(planDir.resolve(name)).size() shouldBe CloneSetIO.read(analyzeDir.resolve(name)).size()
            }
        }
    }

    /** Printed steps, each split into words starting with `mixcr` */
    private fun dryRun(output: Path, vararg presetArgs: String): List<List<String>> {
        val printed = captureStdout {
            run(
                *(listOf("analyze", "--dry-run") + presetArgs + listOf(r1, r2, output).map { it.toString() })
                    .toTypedArray()
            )
        }
        return printed.lines().filter { it.startsWith("mixcr ") }.map { it.split(" ") }
    }

    /** Sample file lists are analyze's own bookkeeping, removed when its JVM exits */
    private fun Path.filesWithExtension(extension: String) =
        listDirectoryEntries()
            .filter { it.extension == extension && !it.name.endsWith(".list.tsv") }
            .map { it.name }
            .sorted()

    private fun run(vararg args: String) {
        TestMain // initializes the system
        Main.execute(*args) shouldBe 0
    }

    private fun captureStdout(action: () -> Unit): String {
        val original = System.out
        val buffer = ByteArrayOutputStream()
        System.setOut(PrintStream(buffer, true))
        try {
            action()
        } finally {
            System.setOut(original)
        }
        return buffer.toString()
    }

    private fun resource(name: String): Path =
        Paths.get(CommandAnalyzeDryRunTest::class.java.getResource("/sequences/$name")!!.toURI())
}
