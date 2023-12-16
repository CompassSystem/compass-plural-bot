import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application

    kotlin("jvm") version "1.9.20"
    kotlin("plugin.serialization") version "1.9.20"

    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "compass_system.compass-plural-bot"
version = "2.0.1"

repositories {
    google()
    mavenCentral()

    maven {
        name = "Sonatype Snapshots (Legacy)"
        url = uri("https://oss.sonatype.org/content/repositories/snapshots")
    }

    maven {
        name = "Sonatype Snapshots"
        url = uri("https://s01.oss.sonatype.org/content/repositories/snapshots")
    }
}

dependencies {
    implementation(kotlin("stdlib"))
	implementation(kotlin("reflect"))
	testImplementation(kotlin("test"))

	implementation("com.kotlindiscord.kord.extensions:kord-extensions:1.7.0-SNAPSHOT")
	implementation("com.kotlindiscord.kord.extensions:adapter-mongodb:1.7.0-SNAPSHOT")

	val ktorVersion = "2.3.6"

	implementation("io.ktor:ktor-client-core:$ktorVersion")
	implementation("io.ktor:ktor-client-cio:$ktorVersion")

	implementation("org.slf4j:slf4j-simple:2.0.9")

	implementation("org.mongodb:mongodb-driver-kotlin-coroutine:4.11.1")
}

tasks {
    test {
        useJUnitPlatform()
    }

    jar {
        manifest.attributes("Main-Class" to "compass_system.compass_plural_bot.EntryKt")
    }

    withType<KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    withType<JavaExec> {
        workingDir("run")
    }
}

application {
    mainClass.set("compass_system.compass_plural_bot.EntryKt")
}
