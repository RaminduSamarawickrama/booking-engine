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
include("libs:architecture")

// Services, one Spring Boot app each.
include("auth")
include("gateway")
include("catalog")
include("pricing")
include("booking")
