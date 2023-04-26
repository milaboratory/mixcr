
import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.SHADOW_GROUP
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.palantir.gradle.gitversion.VersionDetails
import de.undercouch.gradle.tasks.download.Download
import groovy.lang.Closure
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.process.internal.DefaultExecSpec
import org.gradle.process.internal.ExecActionFactory
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import proguard.gradle.ProGuardTask
import java.io.ByteArrayOutputStream
import java.net.InetAddress

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.2.1") {
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
    sourceCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
    withJavadocJar()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
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

    // Snapshot versions of redberry-pipe, milib and repseqio distributed via this repo
    maven {
        url = uri("https://pub.maven.milaboratory.com")
    }

    maven {
        url = uri("s3://milaboratory-artefacts-private-files.s3.eu-central-1.amazonaws.com/maven")
        authentication {
            credentials(AwsCredentials::class) {
                accessKey = miRepoAccessKeyId
                secretKey = miRepoSecretAccessKey
            }
        }
    }
}

val toObfuscate: Configuration by configurations.creating {
    @Suppress("UnstableApiUsage")
    shouldResolveConsistentlyWith(configurations.runtimeClasspath.get())
}

val obfuscationLibs: Configuration by configurations.creating

val mixcrAlgoVersion = "4.3.0-56-alleles"
val milibVersion = "2.4.0-19-alleles"
val mitoolVersion = "1.7.0-11-migex"
val repseqioVersion = "1.8.0-21-master"

val picocliVersion = "4.6.3"
val jacksonBomVersion = "2.14.2"
val milmVersion = "3.5.0"

val cliktVersion = "3.5.0"
val jcommanderVersion = "1.72"

dependencies {
    api("com.milaboratory:milib:$milibVersion")
    api("com.milaboratory:mitool:$mitoolVersion")
    api("io.repseq:repseqio:$repseqioVersion")

    api("com.milaboratory:mixcr-algo:$mixcrAlgoVersion") {
        exclude("com.milaboratory", "mitool")
        exclude("com.milaboratory", "milib")
        exclude("io.repseq", "repseqio")
    }

    toObfuscate("com.milaboratory:mixcr-algo") { exclude("*", "*") }
    toObfuscate("com.milaboratory:milib") { exclude("*", "*") }
    toObfuscate("com.milaboratory:mitool") { exclude("*", "*") }
    toObfuscate("io.repseq:repseqio") { exclude("*", "*") }
    toObfuscate("com.milaboratory:milm2-jvm") { exclude("*", "*") }

    // proguard require classes that were inherited
    obfuscationLibs("com.github.ajalt.clikt:clikt:$cliktVersion") { exclude("*", "*") }
    obfuscationLibs("com.beust:jcommander:$jcommanderVersion") { exclude("*", "*") }

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

    testImplementation("org.lz4:lz4-java:1.8.0")
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
            .forEach { add(project.dependencies.create(it.moduleVersion.asMap())) }
    }
}

val fetchGitTags by tasks.registering(Exec::class) {
    commandLine = listOf("git", "fetch", "--all", "--tags")
}

open class ListGitTagsTask : Exec() {
    @Internal
    val tags: ListProperty<String> = objectFactory.listProperty()

    init {
        commandLine = listOf("git", "tag")
        val output = ByteArrayOutputStream()
        standardOutput = output
        doLast {
            tags.set(output.toString().trim().split("\n"))
        }
    }
}

val listGitTags by tasks.registering(ListGitTagsTask::class) {
    dependsOn(fetchGitTags)
}

@CacheableTask
open class CheckoutPresetsTask @Inject constructor(
    @Inject
    val objectFactory: ObjectFactory,
    @Inject
    val execActionFactory: ExecActionFactory
) : DefaultTask() {
    @Input
    val tags: ListProperty<String> = objectFactory.listProperty()

    @OutputDirectory
    val output: File = project.sourceSets.main.get().output.resourcesDir!!.resolve("mixcr_presets/old_presets")

    @TaskAction
    protected fun exec() {
        val localDirectoryPath = project.buildDir.resolve("git_repo_for_presets")
        localDirectoryPath.mkdirs()

        val remoteRepositoryUrl = "git@github.com:milaboratory/mixcr.git"
        val targetDirectory = "src/main/resources/mixcr_presets"
        execute {
            workingDir(localDirectoryPath)
            commandLine("git", "init", "-q")
        }
        val currentRemote = ByteArrayOutputStream().use { output ->
            execute {
                standardOutput = output
                commandLine("git", "remote")
            }
            output.toString().trim()
        }
        if (currentRemote != "origin") {
            execute {
                workingDir(localDirectoryPath)
                commandLine("git", "remote", "add", "origin", remoteRepositoryUrl)
            }
        }
        execute {
            workingDir(localDirectoryPath)
            commandLine("git", "fetch", "-q", "--all", "--tags")
        }
        execute {
            workingDir(localDirectoryPath)
            commandLine("git", "sparse-checkout", "set", targetDirectory)
        }
        output.deleteRecursively()
        output.mkdirs()
        tags.get().forEach { tag ->
            execute {
                workingDir(localDirectoryPath)
                commandLine("git", "checkout", "-q", "tags/$tag")
            }
            val targetDir = output.resolve(tag.removePrefix("v"))
            localDirectoryPath.resolve(targetDirectory).copyRecursively(targetDir)
            writePresetsList(targetDir, targetDir.resolve("file_list.txt"))
        }
    }

    private fun writePresetsList(dirWithPresets: File, outputFile: File) {
        val yamls = dirWithPresets.walk()
            .filter { it.extension == "yaml" }
            .map { dirWithPresets.toPath().relativize(it.toPath()) }
            .toList()
        outputFile.toPath().toAbsolutePath().parent.toFile().mkdirs()
        outputFile.writeText(
            yamls
                .sorted()
                .joinToString("\n")
        )
    }

    private fun execute(block: ExecSpec.() -> Unit) {
        val execAction = execActionFactory.newExecAction()
        val execSpec = objectFactory.newInstance(DefaultExecSpec::class.java)
        block(execSpec)
        execSpec.copyTo(execAction)
        execAction.execute()
    }
}

val fetchPreviousPresets by tasks.registering(CheckoutPresetsTask::class) {
    group = "build"
    dependsOn(listGitTags)
    tags.set(listGitTags.get().tags.map { tags ->
        tags
            .filter { it.startsWith("v") }
            .map { it.removePrefix("v") }
            .filterNot { it.startsWith("1.") || it.startsWith("2.") || it.startsWith("3.") || it.startsWith("4.0") }
            .map { "v$it" }
    })
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
    property("timestamp", System.currentTimeMillis())
}

@CacheableTask
open class GeneratePresetFileListTask : DefaultTask() {
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    val dirWithPresets = project.file("src/main/resources/mixcr_presets")

    @OutputFile
    val outputFile = project.sourceSets.main.get().output.resourcesDir!!.resolve("mixcr_presets/file_list.txt")

    @TaskAction
    fun run() {
        val yamls = dirWithPresets.walk()
            .filter { it.extension == "yaml" }
            .map { dirWithPresets.toPath().relativize(it.toPath()) }
            .toList()
        outputFile.toPath().toAbsolutePath().parent.toFile().mkdirs()
        outputFile.writeText(
            yamls
                .sorted()
                .joinToString("\n")
        )
    }
}

val generatePresetFileList by tasks.registering(GeneratePresetFileListTask::class) {
    group = "build"
}

tasks.processResources {
    dependsOn(writeBuildProperties)
    dependsOn(generatePresetFileList)
    dependsOn(fetchPreviousPresets)
}

val obfuscate by tasks.registering(ProGuardTask::class) {
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
    from("eclipse-temurin:17-jre")
    label(mapOf("maintainer" to "MiLaboratories Inc <support@milaboratories.com>"))
    runCommand("mkdir /work /opt/${project.name}")
    workingDir("/work")
    environmentVariable("PATH", "/opt/${project.name}:\${PATH}")
    copyFile("LICENSE", "/opt/${project.name}/LICENSE")
    copyFile(project.name, "/opt/${project.name}/${project.name}")
    entryPoint(project.name)
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

