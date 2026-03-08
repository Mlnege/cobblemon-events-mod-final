import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("fabric-loom") version "1.11.1"
    id("maven-publish")
    kotlin("jvm") version "2.2.0"
}

group = "com.cobblemonevents"
version = "2.1.0"

base {
    archivesName.set("cobblemon-events")
}

val minecraftVersion = "1.21.1"
val yarnMappings = "1.21.1+build.3"
val loaderVersion = "0.16.0"
val fabricApiVersion = "0.102.0+1.21.1"
val fabricKotlinVersion = "1.13.4+kotlin.2.2.0"

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/")
    maven("https://cursemaven.com")
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    mappings("net.fabricmc:yarn:$yarnMappings:v2")

    modImplementation("net.fabricmc:fabric-loader:$loaderVersion")
    modImplementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    modImplementation("net.fabricmc:fabric-language-kotlin:$fabricKotlinVersion")

    modImplementation(fileTree("libs") {
        include("*.jar")
    })
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

kotlin {
    jvmToolchain(21)
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(21)
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
    }
}