package com.procureflow;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

/**
 * Guards the modular-monolith rules (see docs/architecture/modular-monolith.md).
 * These tests fail the build the moment a boundary is crossed; extend them as
 * modules gain code (repository placement, DTO direction, event usage).
 */
@AnalyzeClasses(
        packages = "com.procureflow",
        importOptions = com.tngtech.archunit.core.importer.ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    @ArchTest
    static final ArchRule apiMustNotDependOnInfrastructure =
            noClasses().that().resideInAPackage("..api..")
                    .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                    .because("REST controllers must go through application use cases, never infrastructure directly");

    @ArchTest
    static final ArchRule sharedKernelMustNotDependOnModules =
            noClasses().that().resideInAPackage("..shared..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.procureflow.(identity|organization|supplier|procurement|approval|budget|purchaseorder|invoice|notification|audit|analytics|ai)..")
                    .because("the shared kernel is dependency-free by definition");

    @ArchTest
    static final ArchRule noFieldInjection =
            noFields().should().beAnnotatedWith(org.springframework.beans.factory.annotation.Autowired.class)
                    .because("constructor injection keeps dependencies explicit and testable");

    @ArchTest
    static final ArchRule domainMustNotDependOnSpring =
            noClasses().that().resideInAPackage("..domain..")
                    .should().dependOnClassesThat().resideInAPackage("org.springframework..")
                    .because("domain holds business rules only; Spring lives in outer layers");
}
