import com.bmuschko.gradle.docker.tasks.image.DockerBuildImage
import com.bmuschko.gradle.docker.tasks.image.Dockerfile
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.palantir.gradle.gitversion.VersionDetails
import de.undercouch.gradle.tasks.download.Download
import groovy.lang.Closure
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import java.net.InetAddress

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

val dataframeVersion = "0.8.0-rc-8"

plugins {
    `java-library`
    application
    `maven-publish`
    kotlin("jvm") version "1.6.21"
    id("org.jetbrains.kotlin.plugin.dataframe") version "0.8.0-rc-8"
    id("com.palantir.git-version") version "0.13.0" // don't upgrade, latest version that runs on Java 8
    id("com.github.johnrengelman.shadow") version "7.1.2"
    id("com.bmuschko.docker-java-application") version "7.4.0"
    id("de.undercouch.download") version "5.1.0"
}
// Make IDE aware of the generated code:
kotlin.sourceSets.getByName("main").kotlin.srcDir("build/generated/ksp/main/kotlin/")

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
}

val milibVersion = "2.0.0-22-master"
val repseqioVersion = "1.4.1-19-master"
val mitoolVersion = "1.1.0-71-main"
val miplotsVersion = "1.0.0-24-master"
val jacksonBomVersion = "2.13.3"
val redberryPipeVersion = "1.2.0-5-master"

dependencies {
    implementation("cc.redberry:pipe:$redberryPipeVersion")
    implementation("com.milaboratory:milib:$milibVersion") {
        exclude("cc.redberry", "pipe")
    }
    implementation("io.repseq:repseqio:$repseqioVersion") {
        exclude("com.milaboratory", "milib")
    }
    implementation("com.milaboratory:mitool:$mitoolVersion") {
        exclude("com.milaboratory", "milib")
    }
    implementation("com.milaboratory:miplots:$miplotsVersion")

    // implementation("com.milaboratory:milm2-jvm:1.0-SNAPSHOT") { isChanging = true }
    implementation("com.milaboratory:milm2-jvm:2.0.0")

    implementation(platform("com.fasterxml.jackson:jackson-bom:$jacksonBomVersion"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("commons-io:commons-io:2.11.0")
    implementation("org.lz4:lz4-java:1.8.0")
    implementation("net.sf.trove4j:trove4j:3.0.3")
    implementation("info.picocli:picocli:4.6.3")
    implementation("com.google.guava:guava:31.1-jre")
    implementation("com.itextpdf:itext7-core:7.2.1")
    implementation("com.itextpdf:layout:7.2.1")
    implementation("com.github.samtools:htsjdk:2.24.1")
    implementation("org.slf4j:slf4j-nop:1.7.36")

    testImplementation("junit:junit:4.13.2")
    implementation(testFixtures("com.milaboratory:milib:$milibVersion"))
    testImplementation("org.mockito:mockito-all:1.10.19")
    testImplementation("io.kotest:kotest-assertions-core:5.3.0")
}

val writeBuildProperties by tasks.registering(WriteProperties::class) {
    outputFile = file("${sourceSets.main.get().output.resourcesDir}/${project.name}-build.properties")
    property("version", version)
    property("name", "MiXCR")
    property("revision", gitDetails.gitHash)
    property("branch", gitDetails.branchName ?: "no_branch")
    property("host", InetAddress.getLocalHost().hostName)
    property("production", productionBuild == true)
    property("timestamp", System.currentTimeMillis())
}

tasks.processResources {
    dependsOn(writeBuildProperties)
}

val shadowJar = tasks.withType<ShadowJar> {
//    minimize {
//        exclude(dependency("io.repseq:repseqio"))
//        exclude(dependency("com.milaboratory:milib"))
//        exclude(dependency("org.lz4:lz4-java"))
//        exclude(dependency("com.fasterxml.jackson.core:jackson-databind"))
//
//        exclude(dependency("log4j:log4j"))
//        exclude(dependency("org.slf4j:slf4j-api"))
//        exclude(dependency("commons-logging:commons-logging"))
//        exclude(dependency("ch.qos.logback:logback-core"))
//        exclude(dependency("ch.qos.logback:logback-classic"))
//    }
}

val distributionZip by tasks.registering(Zip::class) {
    group = "distribution"
    archiveFileName.set("${project.name}.zip")
    destinationDirectory.set(file("$buildDir/distributions"))
    from(shadowJar) {
        rename("-.*\\.jar", "\\.jar")
    }
    from("${project.rootDir}/${project.name}")
    from("${project.rootDir}/LICENSE")
}

val prepareDockerContext by tasks.registering(Copy::class) {
    group = "docker"
    from(shadowJar) {
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
        if (miRepoAccessKeyId != null) {
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
