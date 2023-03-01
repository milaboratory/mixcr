import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import com.github.jengelman.gradle.plugins.shadow.ShadowJavaPlugin.SHADOW_GROUP
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.palantir.gradle.gitversion.VersionDetails
import de.undercouch.gradle.tasks.download.Download
import groovy.lang.Closure
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.jetbrains.kotlin.gradle.internal.ensureParentDirsCreated
import proguard.gradle.ProGuardTask
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

val mixcrAlgoVersion = "4.2.0-51-proguard"
val milibVersionForTestFixtures = "2.3.0-19-alleles"
val jacksonBomVersion = "2.14.1"
val milmVersion = "2.7.0"

dependencies {
    api("com.milaboratory:mixcr-algo:$mixcrAlgoVersion")
    platform("com.milaboratory:mixcr-algo:$mixcrAlgoVersion")
    implementation("com.milaboratory:milm2-jvm:$milmVersion")

    toObfuscate("com.milaboratory:mixcr-algo") { exclude("*", "*") }
    toObfuscate("com.milaboratory:milib") { exclude("*", "*") }
    toObfuscate("com.milaboratory:mitool") { exclude("*", "*") }
    toObfuscate("com.milaboratory:migex") { exclude("*", "*") }
    toObfuscate("io.repseq:repseqio") { exclude("*", "*") }
    toObfuscate("com.milaboratory:milm2-jvm") { exclude("*", "*") }

    implementation(platform("com.fasterxml.jackson:jackson-bom:$jacksonBomVersion"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("info.picocli:picocli:4.6.3")
    implementation("net.sf.trove4j:trove4j:3.0.3")
    implementation("com.github.victools:jsonschema-generator:4.27.0")
    implementation("com.github.victools:jsonschema-module-jackson:4.27.0")

    runtimeOnly("org.apache.logging.log4j:log4j-core:2.20.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation(testFixtures("com.milaboratory:milib:$milibVersionForTestFixtures"))
    testImplementation("org.mockito:mockito-all:1.10.19")
    testImplementation("io.kotest:kotest-assertions-core:5.3.0")

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
    property("timestamp", System.currentTimeMillis())
}

val generatePresetFileList by tasks.registering {
    group = "build"
    val outputFile = file("${sourceSets.main.get().output.resourcesDir}/mixcr_presets/file_list.txt")
    doLast {
        val yamls = layout.files({
            file("src/main/resources/mixcr_presets").walk()
                .filter { it.extension == "yaml" }
                .map { it.relativeTo(file("src/main/resources/mixcr_presets")) }
                .toList()
        })
        outputFile.ensureParentDirsCreated()
        outputFile.writeText(yamls
            .map { relativePath(it) }
            .sorted()
            .joinToString("\n"))
    }
}

tasks.processResources {
    dependsOn(writeBuildProperties)
    dependsOn(generatePresetFileList)
}

val obfuscate by tasks.registering(ProGuardTask::class) {
    group = "build"

    configuration("mixcr.pro")

    dependsOn(tasks.jar)
    dependsOn(obfuscateRuntime.buildDependencies)

    injars(tasks.jar)
    injars(toObfuscate)
    libraryjars(obfuscateRuntime)

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
    configurations = listOf(obfuscateRuntime)
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
