package com.parallax.engine;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Enforces engine purity (SPEC invariant 1): no I/O, no clock, no randomness, no Spring. */
class PurityArchTest {

    private static final JavaClasses ENGINE = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.parallax.engine");

    @Test
    void engineDependsOnNoBannedPackages() {
        noClasses().that().resideInAPackage("com.parallax.engine..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "java.io..",
                        "java.nio.file..",
                        "java.net..",
                        "java.sql..",
                        "org.springframework..")
                .check(ENGINE);
    }

    @Test
    void engineDependsOnNoClockOrRandomness() {
        noClasses().that().resideInAPackage("com.parallax.engine..")
                .should().dependOnClassesThat().haveFullyQualifiedName("java.time.Clock")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.time.Instant")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.time.LocalDate")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.util.Random")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.util.concurrent.ThreadLocalRandom")
                .orShould().dependOnClassesThat().haveFullyQualifiedName("java.security.SecureRandom")
                .check(ENGINE);
    }

    @Test
    void engineHasNoPublicStaticNonFinalFields() {
        fields().that().arePublic().and().areStatic()
                .should().beFinal()
                .check(ENGINE);
    }
}
