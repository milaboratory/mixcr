package com.milaboratory.mixcr.cli

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import org.junit.Test
import picocli.CommandLine

class MetaInfoTest {
    @Test
    fun `all options must have specified order`() {
        val optionsWithoutOrder = Main.mkCmd().allSubCommands()
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
        val optionsWithoutOrder = Main.mkCmd().allSubCommands()
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
        val optionsWithNotUniqOrder = Main.mkCmd().allSubCommands().flatMap { commandLine ->
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
        Main.mkCmd().allSubCommands().forEach { commandLine ->
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

    private fun CommandLine.allSubCommands(): Collection<CommandLine> = subcommands
        .values
        .filterNot { it.commandSpec.usageMessage().hidden() }
        .flatMap { it.allSubCommands() + it }
}
