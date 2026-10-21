import org.gradle.api.tasks.compile.JavaCompile

plugins {
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(JavaVersion.current().majorVersion.toInt())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(project(":"))
    implementation("com.microsoft:credential-secure-storage:1.0.3")
    implementation("net.java.dev.jna:jna-platform:5.19.1")
    runtimeOnly("org.slf4j:slf4j-nop:1.7.36")

    testImplementation(platform("org.junit:junit-bom:6.1.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("com.fasterxml.jackson.core:jackson-databind:2.21.4")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

application {
    mainClass.set("club.code2create.mcremote.examples.Main")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}

tasks.test {
    useJUnitPlatform()
}

sourceSets.test {
    resources.srcDir(rootProject.file("src/test/resources"))
}

tasks.register<JavaExec>("demo") {
    group = "application"
    description = "Runs the Minecraft Remote for Java learning examples."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set(application.mainClass)
    standardInput = System.`in`
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
}
