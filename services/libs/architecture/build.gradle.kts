// Architecture rules every service checks in its tests (see LayeredArchitecture).
plugins {
    `java-library`
}

dependencies {
    api(libs.archunit.junit5)
}
