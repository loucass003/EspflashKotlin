plugins {
    java
    kotlin("jvm") version "1.9.0"
    kotlin("plugin.serialization") version "1.9.0"

    `maven-publish`
}

group = "dev.llelievr"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.fazecast:jSerialComm:2.10.2")
}

//tasks.named<Test>("test") {
//    useJUnitPlatform()
//}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}
java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "dev.llelievr"
            artifactId = "espflashkotlin"
            version = "1.0.0"

            from(components["java"])
        }
    }
}
