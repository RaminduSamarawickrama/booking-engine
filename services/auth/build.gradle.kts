// auth-service: accounts, passwords, roles, and the ES256 access tokens every other service trusts.
plugins {
    java
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":libs:platform"))
    implementation("org.springframework.security:spring-security-oauth2-jose")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-rabbitmq")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.bootJar {
    archiveFileName = "app.jar"
}
