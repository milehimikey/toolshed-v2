plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    id("org.springframework.boot") version "3.4.0"
    id("io.spring.dependency-management") version "1.1.6"
    id("org.jlleitschuh.gradle.ktlint") version "13.1.0"
}

group = "com.toolshed"
version = "0.1.0-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

repositories {
    mavenCentral()
}

extra["jackson-bom.version"] = "2.22.0"
extra["testcontainers.version"] = "1.21.3"

dependencyManagement {
    imports {
        mavenBom("org.axonframework:axon-framework-bom:5.2.0")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.axonframework.extensions.spring:axon-spring-boot-starter")
    implementation("io.axoniq.framework:axon-server-connector:5.2.0")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("tools.jackson.module:jackson-module-kotlin:3.2.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("io.github.oshai:kotlin-logging-jvm:8.0.4")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.axonframework:axon-test")
    testImplementation("io.axoniq.framework:axoniq-testcontainer:5.2.0")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
    // Several @SpringBootTest contexts (each with an Axon Server container) share one test JVM.
    maxHeapSize = "2g"
    // Docker Desktop rejects the docker-java default API version (1.32); 1.47 is inside its supported range.
    systemProperty("api.version", "1.47")
}
