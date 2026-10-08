// The only service reachable from outside: routes /v1/** to the services, applies CORS,
// rate limits and request ids. It has no database and does not use the platform library.
plugins {
    java
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(platform(libs.spring.cloud.dependencies))
    implementation("org.springframework.cloud:spring-cloud-starter-gateway-server-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Apache HttpClient for proxying: pooled connections, and avoids a JDK HttpClient race on bodiless requests.
    implementation("org.apache.httpcomponents.client5:httpclient5")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.bootJar {
    archiveFileName = "app.jar"
}
