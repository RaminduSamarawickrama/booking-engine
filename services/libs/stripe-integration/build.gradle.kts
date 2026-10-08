// Framework-free Stripe integration shared by payment-service (payments, transfers,
// invoices, webhooks) and fleet-service (driver connected accounts).
plugins {
    `java-library`
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
    // Pinned to the SDK that matches API version 2026-08-26.dahlia; webhook endpoints must use the same version.
    api("com.stripe:stripe-java:33.4.0")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<JavaCompile>().configureEach {
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Xlint:-serial", "-Werror"))
}
