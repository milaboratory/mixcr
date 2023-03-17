package com.milaboratory.mixcr.cli

import com.milaboratory.mixcr.presets.MiXCRCommandDescriptor
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.Test
import picocli.CommandLine

class MetaInfoTest {
    @Test
    fun `all command descriptor must be resolved by name`() {
        MiXCRCommandDescriptor::class.sealedSubclasses.map { it.objectInstance!! }
            .map { it.command }
            .filter { commandName ->
                MiXCRCommandDescriptor.fromStringOrNull(commandName) == null
            } shouldBe emptyList()
    }

    @Test
    fun `almost all commands from analyze should have reset preset option`() {
        val cmd = Main.mkCmd()
        val exclusions = setOf(
            MiXCRCommandDescriptor.findAlleles,
            MiXCRCommandDescriptor.findShmTrees,

            MiXCRCommandDescriptor.align
        )
        (MiXCRCommandDescriptor::class.sealedSubclasses.map { it.objectInstance!! } - exclusions)
            .map { it.command }
            .filter { commandName ->
                val command = cmd.allSubCommands().first { it.commandName == commandName }
                command.commandSpec.findOption("--reset-preset") == null
            } shouldBe emptyList()
    }

    @Test
    fun `almost all commands from analyze should have dont save preset option`() {
        val cmd = Main.mkCmd()
        val exclusions = setOf(
            MiXCRCommandDescriptor.findAlleles,
            MiXCRCommandDescriptor.findShmTrees,

            MiXCRCommandDescriptor.exportClones,
            MiXCRCommandDescriptor.exportAlignments,
            MiXCRCommandDescriptor.qc,
        )
        (MiXCRCommandDescriptor::class.sealedSubclasses.map { it.objectInstance!! } - exclusions)
            .map { it.command }
            .filter { commandName ->
                val command = cmd.allSubCommands().first { it.commandName == commandName }
                command.commandSpec.findOption("--dont-save-preset") == null
            } shouldBe emptyList()
    }

    @Test
    fun `all options must have specified order`() {
        val optionsWithoutOrder = Main.mkCmd().allVisibleSubCommands()
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
        val optionsWithoutOrder = Main.mkCmd().allVisibleSubCommands()
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
        val optionsWithNotUniqOrder = Main.mkCmd().allVisibleSubCommands().flatMap { commandLine ->
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
        Main.mkCmd().allVisibleSubCommands().forEach { commandLine ->
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
