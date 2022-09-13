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
package com.milaboratory.mixcr.export

import com.milaboratory.mixcr.basictypes.VDJCFileHeaderData
import com.milaboratory.mixcr.export.OutputMode.*
import io.repseq.core.GeneType
import io.repseq.core.GeneType.*
import picocli.CommandLine
import java.io.BufferedReader
import java.io.FileReader
import java.util.*

abstract class FieldExtractorsFactory<T : Any> {
    val fields: Array<Field<T>> by lazy {
        val initialized = allAvailableFields()
        check(initialized.map { it.priority }.distinct().size == initialized.size) {
            initialized.groupBy { it.priority }.values
                .filter { it.size > 1 }
                .map { fields -> fields.map { it.cmdArgName } }
                .toString() + " have the same priority"
        }
        check(initialized.map { it.cmdArgName }.distinct().size == initialized.size)
        initialized.sortedBy { it.priority }.toTypedArray()
    }

    protected abstract val presets: Map<String, List<FieldCommandArgs>>

    protected abstract val defaultPreset: String

    protected abstract fun allAvailableFields(): List<Field<T>>

    fun createExtractors(
        header: VDJCFileHeaderData,
        cmdParseResult: CommandLine.ParseResult
    ): List<FieldExtractor<T>> {
        val humanReadable = cmdParseResult.matchedOption("--with-spaces")?.getValue<Boolean>() ?: false
        val oMode = if (humanReadable) HumanFriendly else ScriptingFriendly

        var fields = parseSpec(cmdParseResult)

        // if no options specified
        if (fields.isEmpty()) {
            fields = presets[defaultPreset]!!
        }
        return fields.flatMap { fieldData -> extract(fieldData, header, oMode) }
    }

    fun addOptionsToSpec(spec: CommandLine.Model.CommandSpec, addPresetOptions: Boolean) {
        if (addPresetOptions) {
            val possibleValues = presets.keys.joinToString(", ") { "'$it'" }
            spec.addOption(
                CommandLine.Model.OptionSpec
                    .builder("-p", "--preset")
                    .description("Specify preset of export fields (possible values: $possibleValues; '$defaultPreset' by default)")
                    .required(false)
                    .type(String::class.java)
                    .arity("1")
                    .paramLabel("<preset>")
                    .build()
            )
            spec.addOption(
                CommandLine.Model.OptionSpec
                    .builder("-pf", "--preset-file")
                    .description("Specify preset file of export fields")
                    .required(false)
                    .type(String::class.java)
                    .arity("1")
                    .paramLabel("<presetFile>")
                    .build()
            )
            spec.addArgGroup(
                CommandLine.Model.ArgGroupSpec
                    .builder()
                    .addArg(
                        CommandLine.Model.OptionSpec
                            .builder("-v", "--with-spaces")
                            .description("Output column headers with spaces.")
                            .required(true)
                            .type(Boolean::class.java)
                            .build()
                    )
                    .addArg(
                        CommandLine.Model.OptionSpec
                            .builder("--no-headers")
                            .description("Don't print column names")
                            .required(true)
                            .type(Boolean::class.java)
                            .build()
                    )
                    .exclusive(true)
                    .multiplicity("0..1")
                    .build()
            )
        }
        for (field in fields) {
            spec.addOption(
                CommandLine.Model.OptionSpec
                    .builder(field.cmdArgName)
                    .description(field.description)
                    .required(false)
                    .type(if (field.nArguments > 0) Array<String>::class.java else Boolean::class.javaPrimitiveType)
                    .arity(field.nArguments.toString())
                    .paramLabel(field.metaVars)
                    .hideParamSyntax(true)
                    .hidden(field.deprecation != null)
                    .build()
            )
        }
    }

    private fun hasField(name: String): Boolean =
        fields.any { field -> name.equals(field.cmdArgName, ignoreCase = true) }


    fun extract(
        cmd: FieldCommandArgs,
        header: VDJCFileHeaderData,
        mode: OutputMode
    ): List<FieldExtractor<T>> {
        val field = fields.firstOrNull { f -> cmd.field.equals(f.cmdArgName, ignoreCase = true) }
        field ?: throw IllegalArgumentException("illegal field: " + cmd.field)
        return when (field.nArguments) {
            0 -> {
                require(
                    cmd.args.isEmpty() || (cmd.args.size == 1 && (cmd.args[0].lowercase() in arrayOf("true", "false")))
                )
                listOf(field.create(mode, header, emptyArray()))
            }
            else -> buildList {
                var i = 0
                while (i < cmd.args.size) {
                    add(field.create(mode, header, cmd.args.copyOfRange(i, i + field.nArguments)))
                    i += field.nArguments
                }
            }
        }
    }

    private fun parseFile(file: String): List<FieldCommandArgs> = buildList {
        BufferedReader(FileReader(file)).use { reader ->
            while (true) {
                var line = reader.readLine() ?: break
                line = line.trim { it <= ' ' }
                line = line.replace("\"", "")
                add(FieldCommandArgs(*line.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()))
            }
        }
    }

    fun parseSpec(parseResult: CommandLine.ParseResult): List<FieldCommandArgs> = buildList {
        for (opt in parseResult.matchedOptions()) {
            if (opt.longestName() == "--preset") {
                val preset: String = opt.getValue()
                this += presets[preset]
                    ?: throw IllegalArgumentException("Unknown preset $preset, expected one of ${presets.keys}")
                continue
            }
            if (opt.longestName() == "--preset-file") {
                val presetFile: String = opt.getValue()
                this += parseFile(presetFile)
                continue
            }
            if (!hasField(opt.names()[0])) continue
            val arity = opt.arity().min()
            val actualValue: Array<String>
            if (arity > 0) {
                val value: Array<String> = opt.getValue()
                actualValue = Arrays.copyOf(value, arity)
                opt.setValue(value.copyOfRange(arity, value.size))
            } else {
                actualValue = emptyArray()
            }
            this += FieldCommandArgs(opt.names()[0], actualValue)
        }
    }

    class FieldCommandArgs(val field: String, val args: Array<String>) {
        companion object {
            @Suppress("UNCHECKED_CAST")
            operator fun invoke(vararg args: String): FieldCommandArgs =
                FieldCommandArgs(args[0], args.copyOfRange(1, args.size) as Array<String>)
        }
    }

    @Suppress("ObjectPropertyName")
    object Order {
        const val treeMainParams = 30_000
        const val treeNodeSpecific = 40_000
        const val targetsCount = 50_000
        const val hits = 60_000
        const val alignments = 70_000
        const val features = 80_000
        const val mutations = 90_000
        const val positions = 100_000
        const val readIds = 110_000
        const val cloneSpecific = 120_000
        const val targets = 130_000
        const val readDescriptions = 140_000
        const val alignmentCloneIds = 150_000
        const val identityPercents = 160_000
        const val chains = 170_000
        const val tags = 180_000
        const val treeStats = 190_000

        const val `-nFeature` = features + 100
        const val `-aaFeature` = features + 300
        const val `-lengthOf` = features + 800
        const val `-nMutations` = mutations + 100
        const val `-nMutationsRelative` = mutations + 200
        const val `-aaMutations` = mutations + 300
        const val `-aaMutationsRelative` = mutations + 400
        const val `-mutationsDetailed` = mutations + 500
        const val `-mutationsDetailedRelative` = mutations + 600

        fun orderForBestHit(geneType: GeneType) = hits + 100 + when (geneType) {
            Variable -> 1
            Diversity -> 2
            Joining -> 3
            Constant -> 4
        }
    }
}

