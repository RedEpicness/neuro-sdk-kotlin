plugins {
    `maven-publish`
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"
}

    val jvmVersion = 8

group = "com.github.redepicness"
version = "1.1.2-SNAPSHOT"

dependencies {
    // testImplementation(kotlin("test"))

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    implementation("io.ktor:ktor-client-core:3.1.2")
    implementation("io.ktor:ktor-client-cio:3.1.2")
    implementation("io.ktor:ktor-client-websockets:3.1.2")

    testImplementation("org.slf4j:slf4j-simple:2.0.17")
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(jvmVersion)
    compilerOptions {
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_1)
        moduleName = "neuro-game-sdk"
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(jvmVersion))
    withSourcesJar()
    withJavadocJar()
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/RedEpicness/neuro-game-sdk-kotlin")
            credentials {
                username = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
                password = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications {
        register<MavenPublication>("gpr") {
            from(components["java"])
        }
    }
}
