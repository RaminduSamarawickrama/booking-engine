rootProject.name = "booking-services"

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

// Shared libraries. Services (gateway, auth, catalog, pricing, booking, payment, fleet,
// dispatch, tracking, notification) are added here as each one is implemented.
include("libs:platform")
include("libs:stripe-integration")
