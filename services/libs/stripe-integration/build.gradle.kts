// Framework-free Stripe integration shared by payment-service (payments, transfers,
// invoices, webhooks) and fleet-service (driver connected accounts).
plugins {
    `java-library`
}

dependencies {
    // Pinned to the SDK that matches API version 2026-08-26.dahlia; webhook endpoints must use the same version.
    api(libs.stripe.java)

    testImplementation(platform(libs.spring.boot.dependencies))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
