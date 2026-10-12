plugins {
    // Native handler
    java

    // Apply the application plugin to add support for building a CLI application in Java.
    application

    // Test Coverage
    jacoco

    // GraalVM application builder
    id("org.graalvm.buildtools.native") version "1.1.14"

    // Lombok
    id("io.freefair.lombok") version "9.5.0"
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {

    // ▛▘▛▌▌▌▛▘▛▘█▌
    // ▄▌▙▌▙▌▌ ▙▖▙▖

    // Code utilities
    compileOnly(libs.jspecify)

    // Lombok
    compileOnly("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")

    // Jackson Json Parser
    implementation("tools.jackson.core:jackson-core:3.2.3")
    implementation("tools.jackson.core:jackson-databind:3.2.3")

    // WebP Image Support
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.10.1")

    // ▗     ▗ ▘
    // ▜▘█▌▛▘▜▘▌▛▌▛▌
    // ▐▖▙▖▄▌▐▖▌▌▌▙▌
    //            ▄▌

    // jUnit (Jupiter)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Mockito
    testImplementation("org.mockito:mockito-core:5.16.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.16.0")
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
        vendor.set(JvmVendorSpec.matching("GraalVM"))
    }
}

application {
    // Define the main class for the application.
    mainClass = "prism.Main"
}

graalvmNative {
    agent {
        defaultMode.set("standard")
    }

    binaries {
        all {
            buildArgs.add("-Djava.awt.headless=true")
            buildArgs.add("--add-modules=java.desktop")
        }

        named("main") {
            // Define main package name
            imageName.set("prism")

            // Define code initialization class
            mainClass.set("prism.Main")
        }
    }
}

jacoco {
    toolVersion = "0.8.15"
}

tasks.test {
    useJUnitPlatform()
    jvmArgs("-Dnet.bytebuddy.experimental=true")

    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    reports {
        xml.required = false
        csv.required = false
        html.required = true

        html.outputLocation = layout.projectDirectory.dir("jaCoCo")
    }
}