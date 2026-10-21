import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.TaskAction

abstract class CheckApiReferenceTask : DefaultTask() {
    @get:InputFile
    abstract val generatedReference: RegularFileProperty

    @get:InputFile
    abstract val committedReference: RegularFileProperty

    @TaskAction
    fun verify() {
        check(committedReference.get().asFile.readText() == generatedReference.get().asFile.readText()) {
            "Java API reference is stale. Run ./gradlew generateApiReference"
        }
    }
}

plugins {
    `java-library`
}

group = "club.code2create.mcremote"
description = "Minecraft Remote for Java"

val buildJavaVersion = JavaVersion.current().majorVersion.toInt()
require(buildJavaVersion in listOf(21, 25)) {
    "Use JDK 21 or 25: mise exec java@21 -- ./gradlew <task>"
}

base {
    archivesName.set("minecraft-remote-client")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(buildJavaVersion)
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.21.4")

    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

tasks.wrapper {
    distributionType = Wrapper.DistributionType.BIN
}

val apiReferenceTool = tasks.register<JavaCompile>("compileApiReferenceTool") {
    source("tools/api-reference/ApiReference.java")
    classpath = files()
    destinationDirectory.set(layout.buildDirectory.dir("tools/api-reference"))
    javaCompiler.set(javaToolchains.compilerFor {
        languageVersion = JavaLanguageVersion.of(buildJavaVersion)
    })
}

val renderedApiReference = layout.buildDirectory.file("docs/reference/java-api-reference_ja.md")
val renderApiReference = tasks.register<JavaExec>("renderApiReference") {
    dependsOn(apiReferenceTool)
    javaLauncher.set(javaToolchains.launcherFor {
        languageVersion = JavaLanguageVersion.of(buildJavaVersion)
    })
    classpath = files(apiReferenceTool.flatMap { it.destinationDirectory })
    mainClass.set("ApiReference")
    val metadata = layout.projectDirectory.file("tools/api-reference/descriptions_ja.properties")
    inputs.files(sourceSets.main.get().allJava, metadata)
    inputs.files(configurations.compileClasspath)
    outputs.file(renderedApiReference)
    args(layout.projectDirectory.dir("src/main/java").asFile.absolutePath,
        metadata.asFile.absolutePath, renderedApiReference.get().asFile.absolutePath,
        configurations.compileClasspath.get().asPath)
}

tasks.register<Copy>("generateApiReference") {
    group = "documentation"
    description = "Generates the Japanese Java client API reference from Java source."
    dependsOn(renderApiReference)
    from(renderedApiReference)
    into(layout.projectDirectory.dir("docs"))
}

val checkApiReference = tasks.register<CheckApiReferenceTask>("checkApiReference") {
    group = "verification"
    description = "Checks that the committed Java API reference matches the implementation."
    dependsOn(renderApiReference)
    mustRunAfter(tasks.named("generateApiReference"))
    generatedReference.set(renderedApiReference)
    committedReference.set(layout.projectDirectory.file("docs/java-api-reference_ja.md"))
}

tasks.check {
    dependsOn(checkApiReference)
}
