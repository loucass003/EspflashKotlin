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
    implementation("org.slf4j:slf4j-api:1.7.36")

    testImplementation("ch.qos.logback:logback-classic:1.2.11")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.junit.jupiter:junit-jupiter-engine:5.9.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("com.fazecast:jSerialComm:2.10.2")
}

gradle.taskGraph.whenReady {
    tasks.named<Test>("test") {
        onlyIf {
            // Run tests only if the 'test' task is invoked directly
            this@whenReady.hasTask(":test") && !this@whenReady.hasTask(":build") && !this@whenReady.hasTask(":assemble")
        }
        useJUnitPlatform()
    }
}



tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "17"
    }
    compileTestKotlin {
        kotlinOptions.jvmTarget = "17"
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
