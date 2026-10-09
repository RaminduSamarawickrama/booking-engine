package com.booking.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * The layering every service follows, checked in each service's test suite:
 *
 * <pre>
 *   web, messaging   presentation: REST controllers + DTOs, event listeners
 *        |
 *     service        use cases, transactions, publishing events
 *        |
 *   repository, client   data access (own database) and integration (other services, providers)
 *        |
 *     domain         entities, value objects, business rules; no Spring
 *
 *   config           wiring; may reach any layer
 * </pre>
 *
 * Calls only go down. A controller never touches the database, a repository never calls a
 * service, and domain code never depends on Spring or JDBC.
 */
public final class LayeredArchitecture {

    private LayeredArchitecture() {
    }

    public static void check(String basePackage) {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(basePackage);
        String p = basePackage + ".";

        layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("Web").definedBy(p + "web..")
                .layer("Messaging").definedBy(p + "messaging..")
                .layer("Service").definedBy(p + "service..")
                .layer("Repository").definedBy(p + "repository..")
                .layer("Client").definedBy(p + "client..")
                .layer("Domain").definedBy(p + "domain..")
                .layer("Config").definedBy(p + "config..")
                .whereLayer("Web").mayOnlyBeAccessedByLayers("Config")
                .whereLayer("Messaging").mayNotBeAccessedByAnyLayer()
                .whereLayer("Service").mayOnlyBeAccessedByLayers("Web", "Messaging", "Config")
                .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service", "Config")
                .whereLayer("Client").mayOnlyBeAccessedByLayers("Service", "Config")
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Web", "Messaging", "Service", "Repository", "Client", "Config")
                .withOptionalLayers(true)
                .because("calls only go down the layers")
                .check(classes);

        noClasses().that().resideInAnyPackage(p + "web..", p + "messaging..", p + "service..", p + "domain..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..", "java.sql..")
                .because("only repositories talk to the database")
                .allowEmptyShould(true)
                .check(classes);

        noClasses().that().resideInAPackage(p + "domain..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework..", "jakarta.persistence..",
                        "jakarta.servlet..", "com.booking.platform.web..", "com.booking.platform.messaging..")
                .because("domain code is plain Java: business rules only")
                .allowEmptyShould(true)
                .check(classes);

        classes().that().resideInAPackage(p + "web..").and().haveSimpleNameEndingWith("Controller")
                .should().beAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .allowEmptyShould(true)
                .check(classes);

        classes().that().resideInAPackage(p + "repository..").and().areTopLevelClasses().and().areNotInterfaces()
                .should().haveSimpleNameEndingWith("Repository")
                .allowEmptyShould(true)
                .check(classes);

        classes().that().resideInAPackage(p + "service..").and().areTopLevelClasses().and().areNotInterfaces()
                .and().areAnnotatedWith("org.springframework.stereotype.Service")
                .should().haveSimpleNameEndingWith("Service")
                .allowEmptyShould(true)
                .check(classes);

        noClasses().that().resideOutsideOfPackages(p + "web..", p + "config..")
                .should().dependOnClassesThat().resideInAnyPackage("org.springframework.web.bind.annotation..")
                .because("HTTP mapping belongs to the web layer")
                .allowEmptyShould(true)
                .check(classes);
    }
}
