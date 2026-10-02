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

import com.fasterxml.jackson.module.kotlin.readValue
import com.milaboratory.mixcr.basictypes.CloneSetIO
import com.milaboratory.util.K_OM
import com.milaboratory.util.TempFileManager
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
import kotlin.io.path.readText
import kotlin.io.path.writeText

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

        // Each production step reads the output of the step before it
        val production = steps.filterNot { it[1].startsWith("export") || it[1] == "qc" }
        production.zipWithNext().forEach { (previous, next) ->
            withClue(next.joinToString(" ")) { next[next.size - 2] shouldBe previous.last() }
        }

        val preset = steps.first().windowed(2).single { it[0] == "--preset" }[1].removePrefix("local:")
        Paths.get("$preset.yaml").exists() shouldBe true
    }

    @Test
    fun `json steps are the printed steps`() {
        listOf(
            listOf("10x-sc-xcr-vdj", "--species", "hs"),
            listOf("rna-seq", "--species", "hs"),
        ).forEach { presetArgs ->
            withClue(presetArgs.first()) {
                val dir = TempFileManager.newTempDir().toPath()
                val json = dir.resolve("steps.json")
                val printed = dryRun(dir.resolve("sample"), "--dry-run-json", json.toString(), *presetArgs.toTypedArray())
                printed.shouldNotBeEmpty()
                K_OM.readValue<CommandAnalyze.Cmd.DryRunSteps>(json.toFile()).steps shouldBe printed.map { it.drop(1) }
            }
        }
    }

    @Test
    fun `json file is never overwritten`() {
        val dir = TempFileManager.newTempDir().toPath()
        val json = dir.resolve("steps.json").also { it.writeText("keep") }
        listOf(emptyList(), listOf("-f")).forEach { force ->
            dryRunJsonFails(dir, json, *force.toTypedArray())
            json.readText() shouldBe "keep"
        }
    }

    @Test
    fun `json preset file is never overwritten`() {
        val dir = TempFileManager.newTempDir().toPath()
        val preset = dir.resolve("sample.MiTool.preset.yaml").also { it.writeText("keep") }
        dryRunJsonFails(dir, dir.resolve("steps.json"), "-f", preset = "10x-sc-xcr-vdj")
        preset.readText() shouldBe "keep"
    }

    @Test
    fun `json file can't be the preset file`() {
        val dir = TempFileManager.newTempDir().toPath()
        val preset = dir.resolve("sample.MiTool.preset.yaml")
        dryRunJsonFails(dir, preset, preset = "10x-sc-xcr-vdj")
        preset.readText().trimStart().startsWith("{") shouldBe false
    }

    @Test
    fun `json steps are rejected for what the plan can't express`() {
        val dir = TempFileManager.newTempDir().toPath()
        dryRunJsonFails(dir, dir.resolve("missing").resolve("steps.json"))
        dryRunJsonFails(dir, dir.resolve("steps.json"), "--intermediates-in-temp")
        dryRunJsonFails(dir, dir.resolve("steps.json"), "--remove-intermediates")
        dir.resolve("steps.json").exists() shouldBe false
    }

    private fun dryRunJsonFails(dir: Path, json: Path, vararg args: String, preset: String = "rna-seq") {
        TestMain // initializes the system
        withClue(args.joinToString(" ")) {
            Main.execute(
                "analyze", *args, "--dry-run-json", json.toString(), preset, "--species", "hs",
                r1.toString(), r2.toString(), dir.resolve("sample").toString()
            ) shouldNotBe 0
        }
    }

    @Test
    fun `json steps run one by one match analyze`() {
        listOf(
            listOf("milab-human-dna-xcr-7genes-multiplex"),
            listOf("mikelov-et-al-2021"),
            listOf("10x-sc-xcr-vdj", "--species", "hs"),
            listOf("rna-seq", "--species", "hs"),
        ).forEach { presetArgs ->
            withClue(presetArgs.first()) { checkStepsMatchAnalyze(presetArgs) }
        }
    }

    private fun checkStepsMatchAnalyze(presetArgs: List<String>) {
        val root = TempFileManager.newTempDir().toPath()
        val analyzeDir = root.resolve("analyze").also { it.createDirectories() }
        val stepsDir = root.resolve("steps").also { it.createDirectories() }
        val json = root.resolve("steps.json")

        run(*(listOf("analyze") + presetArgs + listOf(r1, r2, analyzeDir.resolve("sample")).map { it.toString() })
            .toTypedArray())
        run(*(listOf("analyze", "--dry-run-json", json.toString()) + presetArgs +
                listOf(r1, r2, stepsDir.resolve("sample")).map { it.toString() }).toTypedArray())
        K_OM.readValue<CommandAnalyze.Cmd.DryRunSteps>(json.toFile()).steps.forEach { step ->
            withClue(step.joinToString(" ")) { run(*step.toTypedArray()) }
        }

        val analyzeClns = analyzeDir.filesWithExtension("clns") + analyzeDir.filesWithExtension("clna")
        analyzeClns.shouldNotBeEmpty()
        stepsDir.filesWithExtension("clns") + stepsDir.filesWithExtension("clna") shouldBe analyzeClns
        analyzeClns.forEach { name ->
            withClue(name) {
                CloneSetIO.read(stepsDir.resolve(name)).size() shouldBe CloneSetIO.read(analyzeDir.resolve(name)).size()
            }
        }
        stepsDir.filesWithExtension("tsv") shouldBe analyzeDir.filesWithExtension("tsv")
        analyzeDir.filesWithExtension("tsv").forEach { name ->
            withClue(name) { stepsDir.resolve(name).readText() shouldBe analyzeDir.resolve(name).readText() }
        }
    }

    /** Returns the printed steps. Each step is a list of words. The first word is `mixcr`. */
    private fun dryRun(output: Path, vararg args: String): List<List<String>> {
        val printed = captureStdout {
            run(
                *(listOf("analyze", "--dry-run") + args + listOf(r1, r2, output).map { it.toString() })
                    .toTypedArray()
            )
        }
        return printed.lines().filter { it.startsWith("mixcr ") }.map { it.split(" ") }
    }

    /** Ignores the sample file lists. Analyze writes them for its own use and deletes them when its JVM exits. */
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
