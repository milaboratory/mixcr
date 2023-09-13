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

import com.github.victools.jsonschema.generator.CustomDefinition
import com.github.victools.jsonschema.generator.OptionPreset
import com.github.victools.jsonschema.generator.SchemaGenerator
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder
import com.github.victools.jsonschema.generator.SchemaVersion
import com.github.victools.jsonschema.module.jackson.JacksonModule
import com.milaboratory.mitool.report.ReadTrimmerReport
import com.milaboratory.mixcr.alleles.CommandFindAllelesParams
import com.milaboratory.mixcr.alleles.FindAllelesReport
import com.milaboratory.mixcr.assembler.CloneAssemblerParameters
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssemblerParameters
import com.milaboratory.mixcr.assembler.fullseq.FullSeqAssemblerReport
import com.milaboratory.mixcr.assembler.preclone.PreCloneAssemblerParameters
import com.milaboratory.mixcr.assembler.preclone.PreCloneAssemblerReport
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssemblerParameters
import com.milaboratory.mixcr.partialassembler.PartialAlignmentsAssemblerReport
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisParametersIndividual
import com.milaboratory.mixcr.postanalysis.ui.PostanalysisParametersOverlap
import com.milaboratory.mixcr.presets.MiXCRCommandDescriptor
import com.milaboratory.mixcr.presets.MiXCRMixin
import com.milaboratory.mixcr.trees.BuildSHMTreeReport
import com.milaboratory.mixcr.trees.CommandFindShmTreesParams
import com.milaboratory.mixcr.util.VDJCObjectExtenderReport
import com.milaboratory.mixcr.vdjaligners.VDJCAlignerParameters
import com.milaboratory.util.JsonSchemaType
import com.milaboratory.util.K_PRETTY_OM
import com.milaboratory.util.K_YAML_OM
import picocli.CommandLine.Command
import picocli.CommandLine.Parameters
import java.nio.file.Path
import kotlin.reflect.KClass

@Command(
    description = ["Export report schemas"],
    hidden = true
)
class CommandExportSchemas : Runnable {
    @Parameters(index = "0", arity = "1")
    private lateinit var outputPath: Path

    override fun run() {
        outputPath.toFile().deleteRecursively()

        val config = SchemaGeneratorConfigBuilder(
            K_PRETTY_OM,
            SchemaVersion.DRAFT_2020_12,
            OptionPreset.PLAIN_JSON
        ).with(JacksonModule())

        config.forTypesInGeneral()
            .withSubtypeResolver { declaredType, context ->
                val kClass = declaredType.erasedType.kotlin
                if (kClass.isSealed) {
                    val typeContext = context.typeContext
                    kClass.sealedFinalSubclasses()
                        .map { typeContext.resolveSubtype(declaredType, it.java) }
                } else {
                    null
                }
            }
            .withCustomDefinitionProvider { javaType, context ->
                val schema = javaType.erasedType.getAnnotation(JsonSchemaType::class.java)
                if (schema != null) {
                    CustomDefinition(context.createDefinitionReference(context.typeContext.resolve(schema.value.java)))
                } else null
            }

        val generator = SchemaGenerator(config.build())

        //TODO replace with sealedSubclasses after migration
        val reports = arrayOf(
            PreCloneAssemblerReport::class,
            TagReport::class,
            ReadTrimmerReport::class,
            VDJCObjectExtenderReport::class,
            FullSeqAssemblerReport::class,
            RefineTagsAndSortReport::class,
            CloneAssemblerReport::class,
            PartialAlignmentsAssemblerReport::class,
            AlignerReport::class,
            BuildSHMTreeReport::class,
            FindAllelesReport::class,
            ChainUsageStats::class,
        )

        val reportsDir = outputPath.resolve("reports")
        reportsDir.toFile().mkdirs()
        for (report in reports) {
            K_YAML_OM.writeValue(
                reportsDir.resolve("${report.simpleName}.schema.yaml").toFile(),
                generator.generateSchema(report.java)
            )
        }

        //TODO replace with sealedSubclasses after migration
        val parametersForOverride = arrayOf(
            VDJCAlignerParameters::class,
            CloneAssemblerParameters::class,
            PreCloneAssemblerParameters::class,
            FullSeqAssemblerParameters::class,
            PartialAlignmentsAssemblerParameters::class,
            CommandFindAllelesParams::class,
            CommandFindShmTreesParams::class,
            PostanalysisParametersIndividual::class,
            PostanalysisParametersOverlap::class,
        )

        val parametersForOverrideDir = outputPath.resolve("parametersForOverride")
        parametersForOverrideDir.toFile().mkdirs()
        for (parameters in parametersForOverride) {
            K_YAML_OM.writeValue(
                parametersForOverrideDir.resolve("${parameters.simpleName}.schema.yaml").toFile(),
                generator.generateSchema(parameters.java)
            )
        }

        val analyzeBundleDir = outputPath.resolve("analyzeBundle")
        val parametersDir = analyzeBundleDir.resolve("parameters")
        parametersDir.toFile().mkdirs()
        for (command in MiXCRCommandDescriptor::class.sealedFinalSubclasses()) {
            val parameters = command.objectInstance?.paramClass!!
            K_YAML_OM.writeValue(
                parametersDir.resolve("${command.simpleName}.schema.yaml").toFile(),
                generator.generateSchema(parameters.java)
            )
        }

        val mixinsDir = analyzeBundleDir.resolve("mixins")
        mixinsDir.toFile().mkdirs()
        for (mixin in MiXCRMixin::class.sealedFinalSubclasses()) {
            K_YAML_OM.writeValue(
                mixinsDir.resolve("${mixin.simpleName}.schema.yaml").toFile(),
                generator.generateSchema(mixin.java)
            )
        }
    }

    private fun <T : Any> KClass<T>.sealedFinalSubclasses(): List<KClass<out T>> =
        sealedSubclasses
            .flatMap {
                when {
                    it.isFinal -> listOf(it)
                    else -> it.sealedSubclasses
                }
            }
}
