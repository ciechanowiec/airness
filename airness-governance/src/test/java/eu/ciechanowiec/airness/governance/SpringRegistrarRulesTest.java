package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class SpringRegistrarRulesTest {

    private static final String REGISTRAR = "src/main/java/com/example/SampleRegistrar.java";
    private static final String CONFIGURATION = "src/main/java/com/example/Wiring.java";
    private static final String COMPONENTS = "registrars declared as component";
    private static final String UNIMPORTED = "registrars that no configuration imports";
    private static final List<Path> MAIN = List.of(Path.of("src/main/java"));
    private static final List<Path> ALL = List.of(Path.of("src"));

    private static final String PLAIN = """
        package com.example;

        import org.springframework.beans.factory.BeanRegistrar;

        final class SampleRegistrar implements BeanRegistrar {
        }
        """;

    private static final String IMPORTED = """
        package com.example;

        import org.springframework.context.annotation.Configuration;
        import org.springframework.context.annotation.Import;

        @Configuration(proxyBeanMethods = false)
        @Import(SampleRegistrar.class)
        final class Wiring {
        }
        """;

    private static List<Findings> findings(GitFixture fixture, List<Path> roots) {
        return new SpringModuleCheck(fixture.root(), roots, List.of()).findings();
    }

    @Test
    void acceptsARegistrarImportedByAConfiguration() {
        GitFixture fixture = new GitFixture("registrar-imported")
            .write(REGISTRAR, PLAIN)
            .write(CONFIGURATION, IMPORTED);

        assertEquals(
            List.of(), Verdicts.offences(findings(fixture, MAIN), UNIMPORTED),
            "the configuration registers the callback"
        );
    }

    @Test
    void reportsARegistrarNoConfigurationImports() {
        GitFixture fixture = new GitFixture("registrar-unimported").write(REGISTRAR, PLAIN);

        assertEquals(
            1, Verdicts.offences(findings(fixture, MAIN), UNIMPORTED).size(),
            "the callback is otherwise unreachable"
        );
    }

    @Test
    void reportsAComponentRegistrarWithoutDuplicatingTheMissingImport() {
        String component = PLAIN
            .replace(
                "import org.springframework.beans.factory.BeanRegistrar;",
                """
                    import org.springframework.beans.factory.BeanRegistrar;
                    import org.springframework.stereotype.Component;
                    """
            )
            .replace("final class SampleRegistrar", "@Component\nfinal class SampleRegistrar");
        GitFixture fixture = new GitFixture("registrar-component").write(REGISTRAR, component);
        List<Findings> findings = findings(fixture, MAIN);

        assertEquals(1, Verdicts.offences(findings, COMPONENTS).size(), "component scanning is refused");
        assertEquals(
            List.of(), Verdicts.offences(findings, UNIMPORTED),
            "the invalid registration has one owner"
        );
    }

    @Test
    void readsARegistrarFromAnArrayValuedImport() {
        String imported = IMPORTED.replace(
            "@Import(SampleRegistrar.class)",
            "@Import({SampleRegistrar.class, OtherRegistrar.class})"
        );
        GitFixture fixture = new GitFixture("registrar-array")
            .write(REGISTRAR, PLAIN)
            .write(CONFIGURATION, imported);

        assertEquals(List.of(), Verdicts.offences(findings(fixture, MAIN), UNIMPORTED), "arrays bind too");
    }

    @Test
    void refusesATestConfigurationAsProofForAProductionRegistrar() {
        String testConfiguration = CONFIGURATION.replace("src/main", "src/test");
        GitFixture fixture = new GitFixture("registrar-test-import")
            .write(REGISTRAR, PLAIN)
            .write(testConfiguration, IMPORTED);

        assertEquals(
            1, Verdicts.offences(findings(fixture, ALL), UNIMPORTED).size(),
            "test wiring does not register production"
        );
    }

    @Test
    void acceptsFullyQualifiedRegistrarAndImportNames() {
        String registrar = PLAIN
            .replace("import org.springframework.beans.factory.BeanRegistrar;\n\n", "")
            .replace(
                "implements BeanRegistrar",
                "implements org.springframework.beans.factory.BeanRegistrar"
            );
        String imported = IMPORTED.replace(
            "SampleRegistrar.class", "com.example.SampleRegistrar.class"
        );
        GitFixture fixture = new GitFixture("registrar-qualified")
            .write(REGISTRAR, registrar)
            .write(CONFIGURATION, imported);

        assertEquals(List.of(), Verdicts.offences(findings(fixture, MAIN), UNIMPORTED), "names resolve");
    }

    @Test
    void passesOverAnUnrelatedBeanRegistrarInterface() {
        String unrelated = PLAIN.replace(
            "org.springframework.beans.factory.BeanRegistrar", "com.example.framework.BeanRegistrar"
        );
        GitFixture fixture = new GitFixture("registrar-unrelated").write(REGISTRAR, unrelated);

        assertEquals(
            List.of(), Verdicts.offences(findings(fixture, MAIN), UNIMPORTED),
            "the simple name proves no Spring contract"
        );
    }
}
