import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.palantir.gradle.gitversion.VersionDetails
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
description = "MiXCR"

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    withSourcesJar()
    withJavadocJar()
}

application {
    mainClass.set("com.milaboratory.mixcr.cli.Main")
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    options {
        this as StandardJavadocDocletOptions
        addStringOption("Xdoclint:none", "-quiet")
    }
}

repositories {
    // mavenLocal()

    mavenCentral()

    // Snapshot versions of redberry-pipe, milib and repseqio distributed via this repo
    maven {
        url = uri("https://pub.maven.milaboratory.com")
    }
}

val milibVersion = "1.15.0-44-master"
val repseqioVersion = "1.3.5-30-master"
val mitoolVersion = "0.9.1-10-main"
val miplotsVersion = "0.1-19-master"
val jacksonBomVersion = "2.13.3"

dependencies {
    implementation("com.milaboratory:milib:$milibVersion")
    implementation("io.repseq:repseqio:$repseqioVersion") {
        exclude("com.milaboratory", "milib")
    }
    implementation("com.milaboratory:mitool:$mitoolVersion"){
        exclude("com.milaboratory", "milib")
    }
    implementation("com.milaboratory:miplots:$miplotsVersion")

    // implementation("com.milaboratory:milm2-jvm:0.2.0-test-2") { isChanging = true }
    implementation("com.milaboratory:milm2-jvm:1.1.0")

    implementation(platform("com.fasterxml.jackson:jackson-bom:$jacksonBomVersion"))
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("commons-io:commons-io:2.11.0")
    implementation("org.lz4:lz4-java:1.8.0")
    implementation("net.sf.trove4j:trove4j:3.0.3")
    implementation("info.picocli:picocli:4.1.1")
    implementation("com.google.guava:guava:31.1-jre")
    implementation("com.itextpdf:itext7-core:7.2.1")
    implementation("com.itextpdf:layout:7.2.1")

    testImplementation("junit:junit:4.13.2")
    implementation(testFixtures("com.milaboratory:milib:$milibVersion"))
    testImplementation("org.mockito:mockito-all:1.10.19")
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
    from("${project.rootDir}/mixcr")
    from("${project.rootDir}/LICENSE")
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
