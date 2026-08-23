import org.gradle.language.jvm.tasks.ProcessResources

plugins {
    java
    id("org.springframework.boot") version "4.1.0"
}

group = "com.vextis"
version = "0.1.0-SNAPSHOT"
description = "Transactional authority for Vextis CRM, inventory, billing and workflows."

providers.environmentVariable("VEXTIS_GRADLE_BUILD_DIR").orNull?.let {
    layout.buildDirectory.set(file(it))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform("org.springframework.boot:spring-boot-dependencies:4.1.0"))
    implementation(platform("org.springframework.modulith:spring-modulith-bom:2.1.0"))
    implementation(platform("com.google.cloud:libraries-bom:26.86.0"))

    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-graphql")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.modulith:spring-modulith-starter-core")
    implementation("com.google.cloud:google-cloud-pubsub")
    implementation("com.google.cloud.sql:postgres-socket-factory:1.29.0")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-graphql-test")
    testImplementation("org.springframework.graphql:spring-graphql-test")
    testImplementation("org.springframework.modulith:spring-modulith-starter-test")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.4.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.compilerArgs.add("-parameters")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

tasks.named<ProcessResources>("processResources") {
    from("../../contracts/graphql") {
        into("graphql")
        include("*.graphqls")
    }
    from("../..") {
        into("META-INF")
        include("LICENSE", "NOTICE")
    }
}

tasks.withType<Jar>().configureEach {
    manifest {
        attributes(
            "Implementation-Title" to (project.description ?: "Vextis Enterprise Core"),
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "Rafael Patiño Díaz",
            "License" to "Apache-2.0",
        )
    }
}
