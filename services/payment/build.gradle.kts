// payment-service: takes payment for bookings. Mock provider for development, Stripe in test mode.
plugins {
    java
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":libs:platform"))
    implementation(project(":libs:stripe-integration"))
    testImplementation(testFixtures(project(":libs:platform")))
    testImplementation(project(":libs:architecture"))

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
