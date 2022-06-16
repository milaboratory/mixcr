package com.milaboratory.mixcr.export

import com.milaboratory.mixcr.cli.CommandExport.FieldData
import io.repseq.core.GeneFeature
import java.util.*

abstract class BaseFieldExtractors {
    private val fields: Array<Field<out Any>> by lazy {
        initFields()
    }

    protected abstract fun initFields(): Array<Field<out Any>>

    //copy of com.milaboratory.mixcr.cli.CommandExport.extractor
    fun <E> extract(fd: FieldData, clazz: Class<E>, m: OutputMode): List<FieldExtractor<E>> {
        for (f in fields) {
            if (fd.field.equals(f.command, ignoreCase = true) && f.canExtractFrom(clazz)) {
                @Suppress("UNCHECKED_CAST")
                f as Field<E>
                return if (f.nArguments() == 0) {
                    if (!(fd.args.isEmpty() ||
                            fd.args.size == 1 && (fd.args[0].equals("true", ignoreCase = true)
                            || fd.args[0].equals("false", ignoreCase = true)))
                    ) throw RuntimeException()
                    listOf<FieldExtractor<E>>(f.create(m, emptyArray()))
                } else {
                    var i = 0
                    val extractors = ArrayList<FieldExtractor<E>>()
                    while (i < fd.args.size) {
                        extractors.add(f.create(m, Arrays.copyOfRange(fd.args, i, i + f.nArguments())))
                        i += f.nArguments()
                    }
                    extractors
                }
            }
        }
        throw IllegalArgumentException("illegal field: " + fd.field)
    }


    //copy of com.milaboratory.mixcr.export.FeatureExtractors.WithHeader
    protected abstract class WithHeader<T : Any>(
        command: String,
        description: String,
        nArgs: Int,
        clazz: Class<T>,
        private val hPrefix: Array<String>,
        private val sPrefix: Array<String>
    ) : FieldWithParameters<T, Array<GeneFeature>>(clazz, command, description, nArgs) {
        open fun validate(features: Array<GeneFeature>) {}

        private fun header0(prefixes: Array<String>, features: Array<GeneFeature>): String {
            val sb = StringBuilder()
            for (i in prefixes.indices) sb.append(prefixes[i]).append(GeneFeature.encode(features[i]))
            return sb.toString()
        }

        override fun getParameters(strings: Array<String>): Array<GeneFeature> {
            require(strings.size == nArguments) { "Wrong number of parameters for $command" }
            val features = Array<GeneFeature>(strings.size) { i ->
                GeneFeature.parse(strings[i])
            }
            validate(features)
            return features
        }

        override fun getHeader(outputMode: OutputMode, features: Array<GeneFeature>): String =
            FieldExtractors.choose(outputMode, header0(hPrefix, features), header0(sPrefix, features))

        override fun metaVars(): String = when (nArguments) {
            1 -> "<gene_feature>"
            else -> "<gene_feature> <relative_to_gene_feature>"
        }

        companion object {
            inline operator fun <reified T : Any> invoke(
                command: String,
                description: String,
                hPrefix: String,
                sPrefix: String,
                noinline validateArgs: (GeneFeature) -> Unit = {},
                noinline extract: (T, GeneFeature) -> String
            ) = object : WithHeader<T>(command, description, 1, T::class.java, arrayOf(hPrefix), arrayOf(sPrefix)) {
                override fun extractValue(`object`: T, parameters: Array<GeneFeature>): String =
                    extract(`object`, parameters.first())

                override fun validate(features: Array<GeneFeature>) {
                    validateArgs(features.first())
                }
            }

            inline operator fun <reified T : Any> invoke(
                command: String,
                description: String,
                nArgs: Int,
                hPrefix: Array<String>,
                sPrefix: Array<String>,
                noinline validateArgs: (Array<GeneFeature>) -> Unit = {},
                noinline extract: (T, Array<GeneFeature>) -> String
            ) = object : WithHeader<T>(command, description, nArgs, T::class.java, hPrefix, sPrefix) {
                override fun extractValue(`object`: T, parameters: Array<GeneFeature>): String =
                    extract(`object`, parameters)

                override fun validate(features: Array<GeneFeature>) = validateArgs(features)
            }
        }
    }
}

