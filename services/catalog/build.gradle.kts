// catalog-service: vehicle categories, extras, airports and terminals: what customers can book.
plugins {
    java
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":libs:platform"))
    testImplementation(testFixtures(project(":libs:platform")))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-rabbitmq")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.bootJar {
    archiveFileName = "app.jar"
}
