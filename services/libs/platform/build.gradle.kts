// Spring Boot building blocks every service uses: error responses, request ids and
// structured logging, JWT resource-server security, and the transactional outbox/inbox.
plugins {
    `java-library`
    `java-test-fixtures`
}

dependencies {
    api(platform(libs.spring.boot.dependencies))

    api("org.springframework.boot:spring-boot-starter-webmvc")
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.boot:spring-boot-starter-actuator")
    api("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
    api("org.springframework.boot:spring-boot-starter-jdbc")
    api("org.springframework.boot:spring-boot-starter-flyway")
    api("org.springframework.boot:spring-boot-starter-amqp")
    // RestClient picks Apache HttpClient when present: pooled connections and real timeouts.
    api("org.apache.httpcomponents.client5:httpclient5")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testImplementation("org.testcontainers:testcontainers-rabbitmq")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Shared by every service's integration tests: one Postgres and one RabbitMQ per test JVM.
    testFixturesApi(platform(libs.spring.boot.dependencies))
    testFixturesApi("org.springframework.boot:spring-boot-test")
    testFixturesApi("org.springframework.boot:spring-boot-testcontainers")
    testFixturesApi("org.testcontainers:testcontainers-postgresql")
    testFixturesApi("org.testcontainers:testcontainers-rabbitmq")
}
