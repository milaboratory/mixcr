import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.palantir.gradle.gitversion.VersionDetails
import groovy.lang.Closure
import java.net.InetAddress

plugins {
    `java-library`
    application
    `maven-publish`
    id("com.palantir.git-version") version "0.13.0"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

val miRepoAccessKeyId: String by project
val miRepoSecretAccessKey: String by project

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
    (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
}

repositories {
    mavenCentral()

    // Snapshot versions of redberry-pipe, milib and repseqio distributed via this repo
    maven {
        url = uri("https://pub.maven.milaboratory.com")
    }
}

val milibVersion = "1.15.0"
val repseqioVersion = "1.3.5-19-master"
val jacksonVersion = "2.12.4"

dependencies {
    api("com.milaboratory:milib:$milibVersion")
    api("io.repseq:repseqio:$repseqioVersion") {
        exclude("com.milaboratory", "milib")
    }

    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
    implementation("commons-io:commons-io:2.7")
    implementation("org.lz4:lz4-java:1.4.1")
    implementation("net.sf.trove4j:trove4j:3.0.3")
    implementation("info.picocli:picocli:4.1.1")
    implementation("com.google.guava:guava:30.1.1-jre")

    testImplementation("junit:junit:4.13.2")
    implementation(testFixtures("com.milaboratory:milib:$milibVersion"))
    testImplementation("org.mockito:mockito-all:1.9.5")
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

//    miCiStage?.let {
//        if (it == "test") {
//            systemProperty("longTests", "true");
//        }
//    }
    longTests?.let { systemProperty("longTests", it) }
}
