import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.palantir.gradle.gitversion.VersionDetails
import groovy.lang.Closure
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.InetAddress


val miplotsVersion = "1.0.0"
val milibVersion = "2.0.0"
val repseqioVersion = "1.3.5-10-05b9291c5e"
val jacksonVersion = "2.12.4"
val dataframeVersion = "0.8.0-rc-7"

plugins {
    `java-library`
    application
    `maven-publish`
    id("com.palantir.git-version") version "0.13.0"
    id("com.github.johnrengelman.shadow") version "7.0.0"
    kotlin("jvm") version "1.6.20-M1"
    id("org.jetbrains.kotlin.plugin.dataframe") version "0.8.0-rc-7"
}

// Make IDE aware of the generated code:
kotlin.sourceSets.getByName("main").kotlin.srcDir("build/generated/ksp/main/kotlin/")

val miRepoAccessKeyId: String by project
val miRepoSecretAccessKey: String by project

val versionDetails: Closure<VersionDetails> by extra
val gitDetails = versionDetails()

val longTests: String? by project

group = "com.milaboratory"
val gitLastTag = gitDetails.lastTag.removePrefix("v")
version =
    if (gitDetails.commitDistance == 0) gitLastTag
    else "${gitLastTag}-${gitDetails.commitDistance}-${gitDetails.gitHash}"
description = "MiXCR"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
    withJavadocJar()
}

application {
    mainClass.set("com.milaboratory.mixcr.cli.Main")
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc> {
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

tasks.register("createInfoFile") {
    doLast {
        projectDir
            .resolve("build_info.json")
            .writeText("""{"version":"$version"}""")
    }
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")

    // Snapshot versions of milib and repseqio distributed via this repo
    maven {
        url = uri("https://pub.maven.milaboratory.com")
    }
}

dependencies {
    api("com.milaboratory:miplots:$miplotsVersion")
    api("com.milaboratory:milib:$milibVersion")
    api("io.repseq:repseqio:$repseqioVersion") {
        exclude("com.milaboratory", "milib")
    }

    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("commons-io:commons-io:2.7")
    implementation("commons-io:commons-io:2.7")
    implementation("org.lz4:lz4-java:1.4.1")
    implementation("net.sf.trove4j:trove4j:3.0.3")
    implementation("info.picocli:picocli:4.1.1")
    implementation("com.google.guava:guava:30.1.1-jre")

    testImplementation("junit:junit:4.13.2")
    implementation(testFixtures("com.milaboratory:milib:$milibVersion"))
    testImplementation("org.mockito:mockito-all:1.9.5")

    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:dataframe:$dataframeVersion")
    implementation("org.apache.xmlgraphics:fop-transcoder:2.6")
    implementation("org.apache.pdfbox:pdfbox:2.0.21")

    implementation("org.apache.commons:commons-csv:1.9.0")
}

val writeBuildProperties by tasks.registering(WriteProperties::class) {
    outputFile = file("${sourceSets.main.get().output.resourcesDir}/${project.name}-build.properties")
    property("version", version)
    property("name", "MiXCR")
    property("revision", gitDetails.gitHash)
    property("branch", gitDetails.branchName ?: "no_branch")
    property("host", InetAddress.getLocalHost().hostName)
    property("timestamp", System.currentTimeMillis())
}

tasks.processResources {
    dependsOn(writeBuildProperties)
}

val shadowJar = tasks.withType<ShadowJar> {
    minimize {
        exclude(dependency("io.repseq:repseqio"))
        exclude(dependency("com.milaboratory:milib"))
        exclude(dependency("org.lz4:lz4-java"))
        exclude(dependency("com.fasterxml.jackson.core:jackson-databind"))

        exclude(dependency("log4j:log4j"))
        exclude(dependency("org.slf4j:slf4j-api"))
        exclude(dependency("commons-logging:commons-logging"))
        exclude(dependency("ch.qos.logback:logback-core"))
        exclude(dependency("ch.qos.logback:logback-classic"))
    }
}

val distributionZip by tasks.registering(Zip::class) {
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
        maven {
            name = "mipriv"
            url = uri("s3://milaboratory-artefacts-private-files.s3.eu-central-1.amazonaws.com/maven")

            authentication {
                credentials(AwsCredentials::class) {
                    accessKey = miRepoAccessKeyId
                    secretKey = miRepoSecretAccessKey
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

    longTests?.let { systemProperty("longTests", it) }
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    jvmTarget = "11"
}
val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    jvmTarget = "11"
}
