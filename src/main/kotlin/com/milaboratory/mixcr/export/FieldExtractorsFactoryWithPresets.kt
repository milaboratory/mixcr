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

import com.milaboratory.mixcr.basictypes.MiXCRHeader
import com.milaboratory.mixcr.cli.ValidationException
import com.milaboratory.mixcr.cli.logger
import io.repseq.core.GeneType
import io.repseq.core.GeneType.*
import picocli.CommandLine
import picocli.CommandLine.Help.Visibility.ALWAYS
import picocli.CommandLine.Model.ArgGroupSpec
import picocli.CommandLine.Model.OptionSpec
import java.io.BufferedReader
import java.io.FileReader
import java.nio.file.Path
import java.util.*

abstract class FieldExtractorsFactory<T : Any> {
    val fields: Array<FieldsCollection<T>> by lazy {
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

    private val fieldsMap by lazy {
        fields.associateBy { it.cmdArgName.lowercase() }
    }

    operator fun get(fieldName: String): FieldsCollection<T> =
        fieldsMap[fieldName.lowercase()] ?: throw IllegalArgumentException("No such field: $fieldName")

    protected abstract fun allAvailableFields(): List<FieldsCollection<T>>

    open fun addOptionsToSpec(
        fieldsCollector: MutableList<ExportFieldDescription>,
        spec: CommandLine.Model.CommandSpec
    ) {
        val argGroup = ArgGroupSpec.builder()
            .heading("Possible fields to export\n")
            .order(50_000)
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
                        val actualArgs: MutableList<String> = mutableListOf()
                        repeat(argsCountToAdd) {
                            actualArgs.add(args.pop())
                        }
                        fieldsCollector += ExportFieldDescription(field.cmdArgName, actualArgs)
                    }
                    .arity(field.arity.toString())
                    .paramLabel(field.metaVars)
                    .hideParamSyntax(true)
                    .hidden(field.deprecation != null)
                    .order(50_000 + index)
                    .build()
            )
        }
        spec.addArgGroup(argGroup.build())
    }

    /** Creates field extractors from field descriptions */
    fun createExtractors(
        fields: List<ExportFieldDescription>,
        header: MiXCRHeader
    ): List<FieldExtractor<T>> =
        fields.flatMap { fieldDescription ->
            val eField = get(fieldDescription.field)

            eField.deprecation?.let { deprecation ->
                logger.warn(deprecation)
            }

            when (eField.arity.max()) {
                0 -> {
                    require(
                        fieldDescription.args.isEmpty() ||
                                (fieldDescription.args.size == 1 &&
                                        (fieldDescription.args[0].lowercase() in arrayOf("true", "false")))
                    )
                    eField.createFields(header, emptyArray())
                }

                else -> {
                    require(fieldDescription.args.size <= eField.arity.max())
                    eField.createFields(header, fieldDescription.args.toTypedArray())
                }
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
        const val labels = 170_000
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

abstract class FieldExtractorsFactoryWithPresets<T : Any> : FieldExtractorsFactory<T>() {
    protected abstract val presets: Map<String, List<ExportFieldDescription>>

    protected abstract val defaultPreset: String

    override fun addOptionsToSpec(
        fieldsCollector: MutableList<ExportFieldDescription>,
        spec: CommandLine.Model.CommandSpec
    ) {
        super.addOptionsToSpec(fieldsCollector, spec)
        spec.addOption(
            OptionSpec
                .builder("-p", "--preset")
                .description("Specify preset of export fields. Possible values: \${COMPLETION-CANDIDATES}")
                .order(50_000 - 300)
                .required(false)
                .type(String::class.java)
                .defaultValue(defaultPreset)
                .showDefaultValue(ALWAYS)
                .completionCandidates(presets.keys)
                .parameterConsumer { args, _, _ ->
                    val preset: String = args.pop()
                    fieldsCollector += presets[preset]
                        ?: throw IllegalArgumentException("Unknown preset $preset, expected one of ${presets.keys}")
                }
                .arity("1")
                .paramLabel("<preset>")
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
}

