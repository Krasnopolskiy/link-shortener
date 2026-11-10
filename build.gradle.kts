plugins {
    java
    checkstyle
    id("com.diffplug.spotless") version "8.1.0"
}

group = "io.github.krasnopolskiy"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.13.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile> {
    options.release = 21
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf("-Xlint:all,-serial,-try", "-Werror"))
}

tasks.jar {
    archiveFileName = "link-shortener.jar"
    manifest {
        attributes("Main-Class" to "io.github.krasnopolskiy.shortener.Main")
    }
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

checkstyle {
    toolVersion = "14.3.0"
    maxWarnings = 0
}

spotless {
    java {
        target("src/**/*.java")
        prettier(mapOf("prettier" to "3.9.9", "prettier-plugin-java" to "2.11.0"))
            .config(
                mapOf(
                    "parser" to "java",
                    "plugins" to listOf("prettier-plugin-java"),
                    "tabWidth" to 4,
                    "printWidth" to 120,
                ),
            )
    }
}
