package com.milaboratory.mixcr.util

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.module.kotlin.isKotlinClass
import com.milaboratory.mixcr.postanalysis.SetPreprocessorFactory
import com.milaboratory.mixcr.postanalysis.WeightFunction
import com.milaboratory.mixcr.postanalysis.spectratype.SpectratypeKeyFunction
import com.milaboratory.primitivio.Serializer
import com.milaboratory.util.DoNotObfuscateFull
import io.kotest.assertions.asClue
import io.kotest.matchers.shouldBe
import io.repseq.core.Chains
import org.junit.Test
import org.reflections.Reflections
import org.reflections.scanners.Scanners
import java.lang.reflect.GenericArrayType
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.lang.reflect.TypeVariable

class MetaForObfuscationTest {
    private val targetPackages = setOf("com.milaboratory", "io.repseq")

    // packages listed inf mixcr.pro
    private val excludedPackages = listOf(
        "com.milaboratory.mixcr.cli",
        "com.milaboratory.mixcr.presets",
        "com.milaboratory.mixcr.postanalysis.ui",
        "com.milaboratory.milm.metric",
        "com.milaboratory.mitool.cli",
        "com.milaboratory.mitool.refinement.gfilter"
    )

    /**
     * For some reason after obfuscation sometimes it's not working without @JsonCreator
     */
    @Test
    fun `all java constructors with with @JsonProperty must be marker with @JsonCreator`() {
        val result = applicationClasses()
            .filterNot { it.isKotlinClass() }
            .flatMap { it.constructors.toList() }
            .filter { constructor ->
                constructor.parameters.any { parameter ->
                    parameter.getAnnotation(JsonProperty::class.java) != null ||
                            parameter.getAnnotation(JsonValue::class.java) != null
                }
            }
            .filter { it.getAnnotation(JsonCreator::class.java) == null }
        result shouldBe emptyList()
    }

    @Test
    fun `all parameters of @JsonCreator (or equal) java methods must be marked as @JsonProperty or @JsonIgnore or transient`() {
        val exclusions = setOf(
            // by JsonValue
            Chains::class.java
        )

        val result = applicationClasses()
            .filterNot { it.isKotlinClass() }
            .filterNot { it in exclusions }
            .flatMap { subject ->
                val constructorParameters = subject.constructors
                    .filter { constructor ->
                        constructor.getAnnotation(JsonCreator::class.java) != null ||
                                constructor.parameters.any { parameter -> parameter.annotations.any { it.isJackson() } }
                    }
                    .flatMap { constructor -> constructor.parameters.map { constructor to it } }
                val methodParameters = (subject.methods + subject.declaredMethods)
                    .filter { method ->
                        method.getAnnotation(JsonCreator::class.java) != null ||
                                method.parameters.any { parameter -> parameter.annotations.any { it.isJackson() } }
                    }
                    .flatMap { method -> method.parameters.map { method to it } }
                (constructorParameters + methodParameters)
                    .filterNot { (_, parameter) ->
                        parameter.getAnnotation(JsonProperty::class.java) != null ||
                                parameter.getAnnotation(JsonIgnore::class.java) != null
                    }
            }
            .groupBy({ it.first }, { it.second })
        result shouldBe emptyMap()
    }

    @Test
    fun `all classes and superclasses with jackson must be preserved alongside with dependencies`() {
        val jacksonAnnotatedClasses = applicationClasses()
            .filter { it.isJacksonAnnotated() }
            .filterNot {
                // will be kept and no need for dependencies analise
                it.hasCustomSerialization()
            }
        val result = jacksonAnnotatedClasses
            .asSequence()
            .flatMap { clazz ->
                clazz.allSuperClasses() + clazz.interfaces + clazz + clazz.dependencies { !it.hasCustomSerialization() }
            }
            .distinct()
            .filter { it.isFromApplication() }
            .filterNot { it.classWillBeKept() }
            .toList()
        "Classes that must be marked with @DoNotObfuscateFull or it's package must be excluded".asClue {
            result.groupBy { it.`package`.name } shouldBe emptyMap()
        }
    }

    /**
     * Annotation exclusions in proguard are not recursive. But it's more convenient in this way
     */
    @Test
    fun `all classes that are inner to classes annotated by DoNotObfuscateFull should be kept too`() {
        val annotatedClasses = applicationClasses()
            .filter { clazz ->
                clazz.allEnclosingClasses().any { it.getAnnotation(DoNotObfuscateFull::class.java) != null }
            }
        val result = annotatedClasses
            .filterNot { it.isAnonymousClass }
            .filterNot { it.classWillBeKept() }
            .toList()
        "Classes that must be marked with @DoNotObfuscateFull".asClue {
            result.groupBy { it.`package`.name } shouldBe emptyMap()
        }
    }

    private fun Class<*>.allEnclosingClasses(): List<Class<*>> =
        listOfNotNull(enclosingClass) + (enclosingClass?.allEnclosingClasses() ?: emptyList())

    private fun applicationClasses() = targetPackages.flatMap { targetPackage ->
        Reflections(targetPackage)
            .getAll(Scanners.SubTypes)
            .filter { it.startsWith(targetPackage) }
    }
        .map { Class.forName(it) }

    private fun Class<*>.hasCustomSerialization() =
        getAnnotation(JsonSerialize::class.java) != null || getAnnotation(JsonDeserialize::class.java) != null

    private fun Class<*>.isFromApplication() = targetPackages.any {
        `package`?.name?.toString()?.startsWith(it) == true
    }


    private fun Class<*>.allSuperClasses(): List<Class<*>> =
        if (superclass == null || superclass == Object::class.java) {
            emptyList()
        } else {
            listOf(superclass) + superclass.allSuperClasses()
        }

    private fun Class<*>.dependencies(
        result: MutableSet<Class<*>> = mutableSetOf(),
        filter: (Class<*>) -> Boolean
    ): Collection<Class<*>> {
        val referencedTypes = (
                (fields + declaredFields)
                    .filterNot { Modifier.isStatic(it.modifiers) }
                    .map { it.genericType } +
                        (methods + declaredMethods)
                            .filter { method ->
                                (method.parameters.flatMap { it.annotations.toList() } + method.annotations)
                                    .any { it.isJackson() }
                            }
                            .flatMap { method -> method.parameters.map { it.parameterizedType } } +
                        (methods + declaredMethods)
                            .filter { method -> method.annotations.any { it.isJackson() } }
                            .map { it.returnType } +
                        constructors
                            .filter { constructor ->
                                (constructor.parameters.flatMap { it.annotations.toList() } + constructor.annotations)
                                    .any { it.isJackson() }
                            }
                            .flatMap { constructor -> constructor.parameters.map { it.parameterizedType } }
                ).distinct()
        val found = referencedTypes
            .flatMap { type -> type.allGenericTypes() }
            .filter { it.isFromApplication() }
            .filter(filter)
            .toSet()


        val withJsonIgnore = (
                (fields + declaredFields)
                    .filterNot { Modifier.isStatic(it.modifiers) }
                    .filter { it.getAnnotation(JsonIgnore::class.java) != null }
                    .map { it.genericType } +
                        (methods + declaredMethods)
                            .filter { method ->
                                (method.parameters.flatMap { it.annotations.toList() } + method.annotations)
                                    .any { it.isJackson() }
                            }
                            .flatMap { method ->
                                method.parameters
                                    .filter { it.getAnnotation(JsonIgnore::class.java) != null }
                                    .map { it.parameterizedType }
                            } +
                        (methods + declaredMethods)
                            .filter { method -> method.annotations.any { it.isJackson() } }
                            .filter { it.getAnnotation(JsonIgnore::class.java) != null }
                            .map { it.returnType } +
                        constructors
                            .filter { constructor ->
                                (constructor.parameters.flatMap { it.annotations.toList() } + constructor.annotations)
                                    .any { it.isJackson() }
                            }
                            .flatMap { constructor ->
                                constructor.parameters
                                    .filter { it.getAnnotation(JsonIgnore::class.java) != null }
                                    .map { it.parameterizedType }
                            }
                ).distinct()
            .flatMap { type -> type.allGenericTypes() }
            .filter { it.isFromApplication() }
            .filter(filter)
            .toSet()
        val forRecursive = (found - result - withJsonIgnore).filterNot { it.isEnum }
        result += found
        forRecursive.forEach { it.dependencies(result, filter) }
        return result
    }

    private fun Type.allGenericTypes(): List<Class<*>> = when (this) {
        is Class<*> -> listOf(this)
        is ParameterizedType -> allTypes()
        is GenericArrayType -> genericComponentType.allGenericTypes()
        is TypeVariable<*> -> bounds.flatMap { it.allGenericTypes() }
        else -> throw IllegalArgumentException(javaClass.toString())
    }

    private fun ParameterizedType.allTypes(): List<Class<*>> {
        val exclusions = setOf(
            SpectratypeKeyFunction::class.java,
            SetPreprocessorFactory::class.java,
            WeightFunction::class.java
        )
        return if (rawType in exclusions) {
            listOf(rawType as Class<*>)
        } else {
            actualTypeArguments.mapNotNull { it as? Class<*> } + rawType as Class<*>
        }
    }

    private fun Class<*>.classWillBeKept(): Boolean =
        getAnnotation(DoNotObfuscateFull::class.java) != null
                || superclass?.`package`?.name?.startsWith("com.fasterxml.jackson") == true
                || Serializer::class.java in interfaces
                || annotations.any { it.isJackson() }
                || excludedPackages.any { `package`?.name?.startsWith(it) == true }
                || name.contains("Parameters") || enclosingClass?.name?.endsWith("Parameters") == true
                || name.endsWith("Report") || enclosingClass?.name?.endsWith("Report") == true
                || isEnum
                || (`package`?.name?.startsWith("io.repseq.gen.dist") == true && name.endsWith("Model"))
                || (enclosingClass?.`package`?.name?.startsWith("io.repseq.gen.dist") == true && enclosingClass.name.endsWith(
            "Model"
        ))

    private fun Class<*>.isJacksonAnnotated() = allAnnotations().any { it.isJackson() }

    private fun Class<*>.allAnnotations() = annotations.toList() +
            declaredFields.flatMap { it.annotations.toList() } +
            constructors.flatMap { it.parameters.toList() }.flatMap { it.annotations.toList() } +
            constructors.flatMap { it.annotations.toList() } +
            methods.flatMap { it.annotations.toList() } +
            methods.flatMap { it.parameters.toList() }.flatMap { it.annotations.toList() }

    private fun Annotation.isJackson() =
        annotationClass.java.`package`.name.toString().startsWith("com.fasterxml.jackson")
}
