package com.vextis;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ArchitectureTests {

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.vextis");

    @Test
    void verifiesSpringModulithBoundaries() {
        ApplicationModules.of(VextisApplication.class).verify();
    }

    @Test
    void domainDoesNotDependOnFrameworksOrAdapters() {
        noClasses()
                .that().resideInAnyPackage("..domain..")
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework..", "jakarta.persistence..", "..infrastructure..", "..api..")
                .allowEmptyShould(true)
                .check(classes);
    }
}
