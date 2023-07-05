import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.SHADOW_GROUP
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.palantir.gradle.gitversion.VersionDetails
import de.undercouch.gradle.tasks.download.Download
import groovy.lang.Closure
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.jetbrains.kotlin.daemon.common.trimQuotes
import org.jetbrains.kotlin.gradle.internal.ensureParentDirsCreated
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import proguard.gradle.ProGuardTask
import java.net.InetAddress

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.2.2") {
            exclude("com.android.tools.build", "gradle")
        }
    }
}

gradle.startParameter.excludedTaskNames += listOf(
    "assembleDist",
    "assembleShadowDist",
    "distTar",
    "distZip",
    "installDist",
    "installShadowDist",
    "shadowDistTar",
    "shadowDistZip"
)

plugins {
    `java-library`
    application
    `maven-publish`
    kotlin("jvm") version "1.7.10"
    id("com.palantir.git-version") version "0.15.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("com.bmuschko.docker-java-application") version "7.4.0"
    id("de.undercouch.download") version "5.1.0"
}

val miRepoAccessKeyId: String? by project
val miRepoSecretAccessKey: String? by project
val miRepoSessionToken: String? by project

val productionBuild: Boolean? by project

val versionDetails: Closure<VersionDetails> by extra
val gitDetails = versionDetails()

fun boolProperty(name: String): Boolean {
    return ((properties[name] as String?) ?: "false").toBoolean()
}

val isMiCi = boolProperty("mi-ci")
val isRelease = boolProperty("mi-release")

val longTests: String? by project
val miCiStage = properties["mi-ci-stage"] as String?

group = "com.milaboratory"
version = if (version != "unspecified") version else ""
description = "MiXCR"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "11"
}

application {
    mainClass.set("com.milaboratory.mixcr.cli.Main")
    applicationDefaultJvmArgs = listOf("-Xmx8g")
}

tasks.withType<JavaExec> {
    if (project.hasProperty("runWorkingDir")) {
        val runWorkingDir: String by project
        workingDir = file(runWorkingDir)
    }
}

tasks.jar {
    manifest {
        attributes("Main-Class" to "com.milaboratory.mixcr.cli.Main")
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    enabled = false
    // options {
    //     this as StandardJavadocDocletOptions
    //     addStringOption("Xdoclint:none", "-quiet")
    // }
}

repositories {
    // mavenLocal()

    mavenCentral()

    maven {
        url = uri("s3://milaboratory-artefacts-private-files.s3.eu-central-1.amazonaws.com/maven")
        authentication {
            credentials(AwsCredentials::class) {
                accessKey = miRepoAccessKeyId
                secretKey = miRepoSecretAccessKey
                sessionToken = miRepoSessionToken
            }
        }
    }
}

val toObfuscate: Configuration by configurations.creating {
    @Suppress("UnstableApiUsage")
    shouldResolveConsistentlyWith(configurations.runtimeClasspath.get())
}

val obfuscationLibs: Configuration by configurations.creating


val mixcrAlgoVersion = "4.4.0"
val milibVersion = ""
val mitoolVersion = ""
val repseqioVersion = ""

val picocliVersion = "4.6.3"
val jacksonBomVersion = "2.15.2"
val milmVersion = "3.8.0"

val cliktVersion = "3.5.0"
val jcommanderVersion = "1.72"

dependencies {
    if (milibVersion.isNotBlank()) {
        api("com.milaboratory:milib:$milibVersion")
    }
    if (mitoolVersion.isNotBlank()) {
        api("com.milaboratory:mitool:$mitoolVersion")
    }
    if (repseqioVersion.isNotBlank()) {
        api("io.repseq:repseqio:$repseqioVersion")
    }

    api("com.milaboratory:mixcr-algo:$mixcrAlgoVersion")

    toObfuscate("com.milaboratory:mixcr-algo") { exclude("*", "*") }
    toObfuscate("com.milaboratory:milib") { exclude("*", "*") }
    toObfuscate("com.milaboratory:mitool") { exclude("*", "*") }
    toObfuscate("io.repseq:repseqio") { exclude("*", "*") }
    toObfuscate("com.milaboratory:milm2-jvm") { exclude("*", "*") }

    // proguard require classes that were inherited
    obfuscationLibs("com.github.ajalt.clikt:clikt:$cliktVersion") { exclude("*", "*") }

    // required for buildLibrary (to call repseqio)
    implementation("com.beust:jcommander:$jcommanderVersion")

    implementation("com.milaboratory:milm2-jvm:$milmVersion")

    implementation(platform("com.fasterxml.jackson:jackson-bom:$jacksonBomVersion"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("info.picocli:picocli:$picocliVersion")

    // // this way dependency will not be transient, but will be included in application
    // compileOnly("info.picocli:picocli:$picocliVersion")
    // shadow("info.picocli:picocli:$picocliVersion")
    // testImplementation("info.picocli:picocli:$picocliVersion")

    implementation("net.sf.trove4j:trove4j:3.0.3")
    implementation("com.github.victools:jsonschema-generator:4.27.0")
    implementation("com.github.victools:jsonschema-module-jackson:4.27.0")
    implementation("org.lz4:lz4-java:1.8.0")

    shadow("org.apache.logging.log4j:log4j-core:2.20.0")
    testImplementation("org.apache.logging.log4j:log4j-core:2.20.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation(testFixtures("com.milaboratory:milib:$milibVersion"))
    testImplementation("org.mockito:mockito-all:1.10.19")
    testImplementation("io.kotest:kotest-assertions-core:5.3.0")

    // for working reflection scanning
    testImplementation("com.github.ajalt.clikt:clikt:$cliktVersion")
    testImplementation("com.beust:jcommander:$jcommanderVersion")

    testImplementation("org.reflections:reflections:0.10.2")
}

val obfuscateRuntime: Configuration by configurations.creating {
    fun ResolvedModuleVersion.asMap() = mapOf(
        "group" to id.group,
        "name" to id.name,
        "version" to id.version
    )

    defaultDependencies {
        val toExclude = toObfuscate.resolvedConfiguration.resolvedArtifacts
            .map { it.moduleVersion.id.group to it.moduleVersion.id.name }
            .toSet()

        configurations.runtimeClasspath.get().resolvedConfiguration.resolvedArtifacts
            .filterNot { (it.moduleVersion.id.group to it.moduleVersion.id.name) in toExclude }
            .forEach {
                add(
                    project.dependencies.create(it.moduleVersion.asMap())
                )
            }
    }
}

val writeBuildProperties by tasks.registering(WriteProperties::class) {
    group = "build"
    outputFile = file("${sourceSets.main.get().output.resourcesDir}/${project.name}-build.properties")
    property("version", version)
    property("name", "MiXCR")
    property("revision", gitDetails.gitHash)
    property("branch", gitDetails.branchName ?: "no_branch")
    property("host", InetAddress.getLocalHost().hostName)
    property("production", productionBuild == true)
    property("timestamp", if (isMiCi) System.currentTimeMillis() else 0L)
}

val unzipOldPresets by tasks.registering(Copy::class) {
    val outputDir = sourceSets.main.get().output.resourcesDir!!.resolve("presets/old_version")
    doFirst {
        outputDir.deleteRecursively()
        outputDir.mkdirs()
    }

    val archives = projectDir.resolve("old_presets").listFiles()!!

    archives.forEach { archive ->
        from(tarTree(archive))
        into(outputDir)
    }
}

val generatePresetFileList by tasks.registering {
    group = "build"
    val outputFile = sourceSets.main.get().output.resourcesDir!!.resolve("presets/file_list.txt")
    doLast {
        val source = sourceSets.main.get().output.resourcesDir!!.resolve("presets")
        val yamls = layout.files({
            source.walk()
                .filter { it.extension == "yaml" }
                .toList()
        })
        outputFile.ensureParentDirsCreated()
        outputFile.writeText(
            yamls
                .flatMap { file ->
                    val presetNames = mutableListOf<String>()
                    val deprecatedPresets = mutableSetOf<String>()
                    val abstractPresets = mutableSetOf<String>()
                    file.useLines { lines ->
                        for (line in lines) {
                            if (!line.startsWith("  ") && line.isNotBlank() && !line.startsWith("#")) {
                                presetNames += line.removeSuffix(":").trimQuotes()
                            }
                            if (line.startsWith("  deprecation:") || line.startsWith("  \"deprecation\":")) {
                                deprecatedPresets += presetNames.last()
                            }
                            if (line == "  abstract: true" || line == "  \"abstract\": true") {
                                abstractPresets += presetNames.last()
                            }
                        }
                    }
                    presetNames.map { name ->
                        val pathToSave = relativePath(file.relativeTo(source))
                        listOf(
                            name,
                            (name in deprecatedPresets).toString(),
                            (name in abstractPresets).toString(),
                            pathToSave
                        )
                    }
                }
                .sortedBy { (name) -> name }
                .joinToString("\n") { line -> line.joinToString("\t") }
        )
    }
}

tasks.processResources {
    dependsOn(unzipOldPresets)
    dependsOn(writeBuildProperties)
    finalizedBy(generatePresetFileList)
}

val checkObfuscation by tasks.registering(Test::class) {
    group = "verification"

    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath

    include("**/MetaForObfuscationTest*")
    useJUnit()
}

val obfuscate by tasks.registering(ProGuardTask::class) {
    dependsOn(checkObfuscation)
    group = "build"

    configuration("mixcr.pro")

    dependsOn(tasks.jar)
    dependsOn(obfuscateRuntime.buildDependencies)

    injars(tasks.jar)
    injars(toObfuscate)
    libraryjars(obfuscateRuntime)
    libraryjars(configurations.shadow)
    libraryjars(obfuscationLibs)

    outjars(buildDir.resolve("libs/mixcr-obfuscated.jar"))

    printconfiguration(buildDir.resolve("proguard.config.pro"))
    printmapping(buildDir.resolve("proguard-mapping.txt"))

    if (org.gradle.internal.jvm.Jvm.current().jre == null)
        listOf("java.base.jmod", "java.prefs.jmod", "java.scripting.jmod").map {
            libraryjars(
                hashMapOf("jarfilter" to "!**.jar", "filter" to "!module-info.class"),
                org.gradle.internal.jvm.Jvm.current().javaHome.resolve("jmods/${it}").absolutePath
            )
        }
    else
        listOf("rt.jar", "jce.jar", "jsse.jar").map {
            libraryjars(org.gradle.internal.jvm.Jvm.current().jre!!.resolve("lib/${it}"))
        }
}

val shadowJarAfterObfuscation by tasks.creating(ShadowJar::class) {
    group = SHADOW_GROUP
    description = "Create a combined JAR of obfuscated project and runtime dependencies"
    manifest.inheritFrom(tasks.jar.get().manifest)
    dependsOn(obfuscate)
    from(obfuscate.get().outJarFiles)
    archiveClassifier.set("all")
    // copy from com/github/jengelman/gradle/plugins/shadow/ShadowJavaPlugin.groovy:86
    exclude("META-INF/INDEX.LIST", "META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "module-info.class")
    configurations = listOf(obfuscateRuntime, project.configurations.shadow.get())
}

tasks.named<ShadowJar>("shadowJar") {
    configurations += project.configurations.shadow.get()
}

val distributionZip by tasks.registering(Zip::class) {
    group = "distribution"
    archiveFileName.set("${project.name}.zip")
    destinationDirectory.set(file("$buildDir/distributions"))
    from(shadowJarAfterObfuscation) {
        rename("-.*\\.jar", "\\.jar")
    }
    from("${project.rootDir}/${project.name}")
    from("${project.rootDir}/LICENSE")
}

val prepareDockerContext by tasks.registering(Copy::class) {
    group = "docker"
    from(shadowJarAfterObfuscation) {
        rename("-.*\\.jar", "\\.jar")
    }
    from("${project.rootDir}/${project.name}")
    from("${project.rootDir}/LICENSE")
    into(layout.buildDirectory.dir("docker"))
}

val prepareIMGTDockerContext by tasks.registering(Download::class) {
    group = "docker"
    dependsOn(prepareDockerContext)
    src("https://github.com/repseqio/library-imgt/releases/download/v8/imgt.202214-2.sv8.json.gz")
    dest(layout.buildDirectory.dir("docker"))
}

val commonDockerContents: Dockerfile.() -> Unit = {
    from("amazoncorretto:17")
    label(mapOf("maintainer" to "MiLaboratories Inc <support@milaboratories.com>"))
    runCommand("mkdir /work /opt/${project.name}")
    runCommand("yum install procps -y") // Needed for image compatibility with nextflow
    workingDir("/work")
    environmentVariable("PATH", "/opt/${project.name}:\${PATH}")
    copyFile("LICENSE", "/opt/${project.name}/LICENSE")
    copyFile(project.name, "/opt/${project.name}/${project.name}")
    copyFile("${project.name}.jar", "/opt/${project.name}/${project.name}.jar")
}

val createDockerfile by tasks.registering(Dockerfile::class) {
    group = "docker"
    dependsOn(prepareDockerContext)
    commonDockerContents()
}

val imgtDockerfile = layout.buildDirectory.file("docker/Dockerfile.imgt")

val createIMGTDockerfile by tasks.registering(Dockerfile::class) {
    group = "docker"
    dependsOn(createDockerfile)
    dependsOn(prepareIMGTDockerContext)
    destFile.set(imgtDockerfile)
    commonDockerContents()
    copyFile("imgt*", "/opt/${project.name}/")
}

val buildDockerImage by tasks.registering(DockerBuildImage::class) {
    group = "docker"
    dependsOn(createDockerfile)
    images.set(setOf(project.name) + if (version == "") emptySet() else setOf("${project.name}:${version}"))
}

val buildIMGTDockerImage by tasks.registering(DockerBuildImage::class) {
    group = "docker"
    dependsOn(createIMGTDockerfile)
    dockerFile.set(imgtDockerfile)
    images.set(setOf(project.name + ":latest-imgt") + if (version == "") emptySet() else setOf("${project.name}:${version}-imgt"))
}

publishing {
    repositories {
        if (miRepoAccessKeyId != null && miRepoSecretAccessKey != null) {
            maven {
                name = "mipriv"
                url = uri("s3://milaboratory-artefacts-private-files.s3.eu-central-1.amazonaws.com/maven")

                authentication {
                    credentials(AwsCredentials::class) {
                        accessKey = miRepoAccessKeyId!!
                        secretKey = miRepoSecretAccessKey!!
                        sessionToken = miRepoSessionToken
                    }
                }
            }
        }
    }

    publications.create<MavenPublication>("mavenJava") {
        from(components["java"])
    }
}

tasks.test {
    useJUnit()
    minHeapSize = "1024m"
    maxHeapSize = "2048m"

    testLogging {
        showStandardStreams = true
        exceptionFormat = FULL
    }

    miCiStage?.let {
        if (it == "test") {
            systemProperty("longTests", "true")
        }
    }
    longTests?.let { systemProperty("longTests", it) }
}

