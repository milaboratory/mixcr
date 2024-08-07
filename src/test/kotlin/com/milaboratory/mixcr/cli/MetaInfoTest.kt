package com.milaboratory.mixcr.cli

import com.milaboratory.mixcr.presets.AnalyzeCommandDescriptor
import com.milaboratory.mixcr.presets.MiXCRCommandDescriptor
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.Test
import picocli.CommandLine
import kotlin.reflect.KClass

class MetaInfoTest {
    @Test
    fun `all command descriptor must be resolved by name`() {
        mixcrCommandDescriptions()
            .map { it.command }
            .filter { commandName ->
                AnalyzeCommandDescriptor.fromStringOrNull(commandName) == null
            } shouldBe emptyList()
    }

    @Test
    fun `almost all commands from analyze should have reset preset option`() {
        val cmd = Main.mkCmd(emptyArray())
        val exclusions = setOf(
            MiXCRCommandDescriptor.findAlleles,
            MiXCRCommandDescriptor.findShmTrees,

            AnalyzeCommandDescriptor.align
        )
        (mixcrCommandDescriptions() - exclusions)
            .filterNot { it.command.startsWith("mitool") }
            .map { it.command }
            .filter { commandName ->
                val command = cmd.allSubCommands().first { it.commandName == commandName }
                command.commandSpec.findOption("--reset-preset") == null
            } shouldBe emptyList()
    }

    @Test
    fun `almost all commands from analyze should have dont save preset option`() {
        val cmd = Main.mkCmd(emptyArray())
        val exclusions = setOf(
            MiXCRCommandDescriptor.findAlleles,
            MiXCRCommandDescriptor.findShmTrees,

            AnalyzeCommandDescriptor.exportClones,
            AnalyzeCommandDescriptor.exportCloneGroups,
            AnalyzeCommandDescriptor.exportAlignments,
            AnalyzeCommandDescriptor.qc,
        )
        (mixcrCommandDescriptions() - exclusions)
            .filterNot { it.command.startsWith("mitool") }
            .map { it.command }
            .filter { commandName ->
                val command = cmd.allSubCommands().first { it.commandName == commandName }
                command.commandSpec.findOption("--dont-save-preset") == null
            } shouldBe emptyList()
    }

    private fun mixcrCommandDescriptions() =
        AnalyzeCommandDescriptor::class.resolveSealedSubclasses().map { it.objectInstance!! }

    private fun <T : Any> KClass<T>.resolveSealedSubclasses(): List<KClass<out T>> =
        when {
            isSealed -> sealedSubclasses.flatMap { it.resolveSealedSubclasses() }
            else -> listOf(this)
        }


    @Test
    fun `all options must have specified order`() {
        val optionsWithoutOrder = Main.mkCmd(emptyArray()).allVisibleSubCommands()
            .flatMap { commandLine ->
                commandLine.commandSpec.options()
                    .filterNot { it.hidden() }
                    .filter { it.order() == -1 }
                    .map { commandLine.commandName + " " + it.longestName() }
            }
        optionsWithoutOrder shouldBe emptyList()
    }

    @Test
    fun `all required options must be first in help`() {
        val optionsWithoutOrder = Main.mkCmd(emptyArray()).allVisibleSubCommands()
            .flatMap { commandLine ->
                val (required, notRequired) = commandLine.commandSpec.options()
                    .filterNot { it.hidden() }
                    .filter { it.group() == null }
                    .partition { it.required() }
                val minOrderForNotRequired = notRequired.map { it.order() }.min()
                required
                    .filter { it.order() >= minOrderForNotRequired }
                    .map { commandLine.commandName + " " + it.longestName() }
            }
        optionsWithoutOrder shouldBe emptyList()
    }

    @Test
    fun `all arg groups must have specified order`() {
        val optionsWithNotUniqOrder = Main.mkCmd(emptyArray()).allVisibleSubCommands().flatMap { commandLine ->
            commandLine.commandSpec.argGroups()
                .filter { it.order() == -1 }
                .map { argGroupSpec ->
                    commandLine.commandName + " " + argGroupSpec.heading() + " " + argGroupSpec
                        .options().map { it.longestName() }
                }
        }
        optionsWithNotUniqOrder shouldBe emptyList()
    }

    @Test
    fun `all options must uniq order`() {
        Main.mkCmd(emptyArray()).allVisibleSubCommands().forEach { commandLine ->
            val notUniqOrders = commandLine.commandSpec.options()
                .filterNot { it.hidden() }
                .groupBy { it.order() }
                .filterValues { it.size > 1 }
                .values.flatten()
                .map { it.longestName() }
            withClue(commandLine.commandName) {
                notUniqOrders shouldBe emptyList()
            }
        }
    }

    private fun CommandLine.allVisibleSubCommands(): Collection<CommandLine> = subcommands
        .values
        .filterNot { it.commandSpec.usageMessage().hidden() }
        .flatMap { it.allVisibleSubCommands() + it }

    private fun CommandLine.allSubCommands(): Collection<CommandLine> = subcommands
        .values
        .flatMap { it.allSubCommands() + it }
}
