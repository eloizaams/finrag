plugins {
    kotlin("jvm") version "2.3.21"
    kotlin("plugin.spring") version "2.3.21"
    kotlin("plugin.jpa") version "2.3.21"
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("org.jetbrains.kotlinx.kover") version "0.9.8"
}

group = "com.eloiza"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.apache.pdfbox:pdfbox:3.0.7")
    implementation("org.hibernate.orm:hibernate-vector")
    implementation("org.springframework.boot:spring-boot-restclient")
    implementation("io.jsonwebtoken:jjwt-api:0.13.0")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.3")
    implementation("com.bucket4j:bucket4j_jdk17-core:8.14.0")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.13.0")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.13.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-resttestclient")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("io.kotest:kotest-runner-junit5:6.2.2")
    testImplementation("io.kotest:kotest-assertions-core:6.2.2")
    testImplementation("io.kotest:kotest-extensions-spring:6.2.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

kover {
    currentProject {
        instrumentation {
            // ragEval chama a API real da OpenAI e fica fora do escopo de cobertura (specs/M8-design.md)
            disabledForTestTasks.add("ragEval")
        }
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    environment("JWT_SECRET", "test-only-secret-do-not-use-in-production-1234567890")
    environment("OPENAI_API_KEY", "test-only-openai-key-not-real")
    environment("ANTHROPIC_API_KEY", "test-only-anthropic-key-not-real")
}

// a avaliação de RAG (tag RagEval) usa a API real da OpenAI e fica fora do
// ciclo padrão de build/CI — roda só via ./gradlew ragEval (specs/M8-design.md)
tasks.test {
    systemProperty("kotest.tags", "!RagEval")
}

tasks.register<Test>("ragEval") {
    description = "Avalia o retrieval do RAG contra o golden dataset (chama a API real da OpenAI)"
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    systemProperty("kotest.tags", "RagEval")
    outputs.upToDateWhen { false }
    doFirst {
        val key = System.getenv("OPENAI_API_KEY")
        require(!key.isNullOrBlank() && !key.startsWith("test-only")) {
            "defina a chave real: OPENAI_API_KEY=... ./gradlew ragEval"
        }
        // sobrescreve a chave fake herdada do bloco withType<Test> acima
        environment("OPENAI_API_KEY", key)
    }
}
