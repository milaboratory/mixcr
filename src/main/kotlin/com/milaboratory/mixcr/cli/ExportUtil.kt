/*
 * Copyright (c) 2014-2023, MiLaboratories Inc. All Rights Reserved
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
import com.milaboratory.mixcr.cli.CommonDescriptions.Labels
import com.milaboratory.mixcr.export.ExportFieldDescription
import com.milaboratory.mixcr.export.FieldExtractorsFactory
import com.milaboratory.mixcr.export.FieldExtractorsFactoryWithPresets
import picocli.CommandLine
import picocli.CommandLine.Model.ArgGroupSpec
import picocli.CommandLine.Model.OptionSpec
import java.io.BufferedReader
import java.io.FileReader
import java.nio.file.Path

fun <T : Any> FieldExtractorsFactory<T>.addOptionsToSpec(
    fieldsCollector: MutableList<ExportFieldDescription>,
    spec: CommandLine.Model.CommandSpec
) {
    val argGroup = ArgGroupSpec.builder()
        .heading("Possible fields to export\n")
        .order(FieldExtractorsFactory.globalOrderInCli)
        .validate(false)
        .exclusive(false)

    fields.forEachIndexed { index, field ->
        argGroup.addArg(
            OptionSpec
                .builder(field.cmdArgName)
                .description(field.description)
                .required(false)
                .type(if (field.arity.max() > 0) Array<Array<String>>::class.java else Boolean::class.javaPrimitiveType)
                .parameterConsumer { args, _, _ ->
                    val argsCountToAdd = field.consumableArgs(args.reversed())
                    if (argsCountToAdd > args.size) {
                        throw ValidationException("Not enough parameters for ${field.cmdArgName}")
                    }
                    val actualArgs = mutableListOf<String>()
                    repeat(argsCountToAdd) {
                        actualArgs.add(args.pop())
                    }
                    field.tryParseArgs(actualArgs)
                    fieldsCollector += ExportFieldDescription(field.cmdArgName, actualArgs)
                }
                .arity(field.arity.toString())
                .paramLabel(field.metaVars)
                .hideParamSyntax(true)
                .hidden(field.deprecation != null)
                .order(FieldExtractorsFactory.globalOrderInCli + index)
                .completionCandidates(field.completionCandidates)
                .build()
        )
    }
    spec.addArgGroup(argGroup.build())
    if (this is FieldExtractorsFactoryWithPresets) {
        spec.addOption(
            OptionSpec
                .builder("-p", "--preset")
                .description("Specify preset of export fields. Possible values: \${COMPLETION-CANDIDATES}. By default `$defaultPreset`")
                .order(50_000 - 300)
                .required(false)
                .type(String::class.java)
                .completionCandidates(presets.keys)
                .parameterConsumer { args, _, _ ->
                    val preset: String = args.pop()
                    fieldsCollector += presets[preset]
                        ?: throw IllegalArgumentException("Unknown preset $preset, expected one of ${presets.keys}")
                }
                .arity("1")
                .paramLabel(Labels.PRESET)
                .build()
        )
        spec.addOption(
            OptionSpec
                .builder("-pf", "--preset-file")
                .description("Specify preset file of export fields")
                .order(50_000 - 200)
                .required(false)
                .type(Path::class.java)
                .parameterConsumer { args, _, _ ->
                    val presetFile: String = args.pop()
                    fieldsCollector += parseFile(presetFile)
                }
                .arity("1")
                .paramLabel("<presetFile>")
                .build()
        )
    }
}

private fun parseFile(file: String): List<ExportFieldDescription> = buildList {
    BufferedReader(FileReader(file)).use { reader ->
        while (true) {
            var line = reader.readLine() ?: break
            line = line.trim { it <= ' ' }
            line = line.replace("\"", "")
            add(ExportFieldDescription(*line.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()))
        }
    }
}
