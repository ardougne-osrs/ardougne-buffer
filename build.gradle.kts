plugins {
    id("maven-publish")
    kotlin("jvm") version "1.9.0"
}

group = "io.ardougne"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    api("io.netty:netty-buffer:4.1.100.Final")
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/ardougne-osrs/ardougne-buffer")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
    publications {
        register<MavenPublication>("default") {
            from(components["java"])
            from(components["kotlin"])
        }
    }
}