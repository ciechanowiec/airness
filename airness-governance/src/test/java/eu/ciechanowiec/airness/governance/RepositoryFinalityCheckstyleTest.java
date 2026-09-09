package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.CheckstyleConfigurationTest.Finding;
import eu.ciechanowiec.airness.governance.CheckstyleConfigurationTest.Fixture;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryFinalityCheckstyleTest {

    private static final String DEMANDED = "RequireFinalClass";
    private static final String STEREOTYPE = "org.springframework.stereotype.Repository";
    private static final String DECLARATION = "class Catalogue";
    private static final String REPOSITORY = """
        package example;
        import org.springframework.stereotype.Repository;
        @Repository
        class Catalogue {
            String read(String key) {
                return key.strip();
            }
        }
        """;

    @Test
    void acceptsAnImportedSpringRepository(@TempDir Path directory) {
        assertFalse(inspect(directory, REPOSITORY, true).stream().anyMatch(finding -> finding.is(DEMANDED)));
    }

    @Test
    void acceptsAQualifiedSpringRepository(@TempDir Path directory) {
        String source = REPOSITORY.replace("import " + STEREOTYPE + ";", "")
            .replace("@Repository", "@" + STEREOTYPE);
        assertFalse(inspect(directory, source, true).stream().anyMatch(finding -> finding.is(DEMANDED)));
    }

    @Test
    void acceptsAnExplicitRepositoryBeanName(@TempDir Path directory) {
        String source = REPOSITORY.replace("@Repository", "@Repository(value = \"catalogue\")");
        assertFalse(inspect(directory, source, true).stream().anyMatch(finding -> finding.is(DEMANDED)));
    }

    @Test
    void distinguishesLocalAnnotationScopes(@TempDir Path directory) {
        String own = REPOSITORY.replace("    String read", "    @interface Repository {}\n    String read");
        String enclosing = REPOSITORY.replace(
            "@Repository\nclass Catalogue",
            "final class Envelope {\n@interface Repository {}\n@Repository\nclass Catalogue"
        ) + "}\n";
        String unrelated = REPOSITORY + "final class Other { @interface Repository {} }\n";
        assertFalse(inspect(directory, own, true).stream().anyMatch(finding -> finding.is(DEMANDED)));
        assertTrue(inspect(directory, enclosing, true).stream().anyMatch(finding -> finding.is(DEMANDED)));
        assertFalse(inspect(directory, unrelated, true).stream().anyMatch(finding -> finding.is(DEMANDED)));
    }

    @Test
    void retainsThePlainJavaRequirement(@TempDir Path directory) {
        assertTrue(inspect(directory, REPOSITORY, false).stream().anyMatch(finding -> finding.is(DEMANDED)));
    }

    @Test
    void retainsOrdinaryComponentsAndOtherAnnotations(@TempDir Path directory) {
        List<String> sources = List.of(
            REPOSITORY.replace(STEREOTYPE, "example.Repository"),
            REPOSITORY.replace("Repository", "Component"),
            REPOSITORY.replace("import " + STEREOTYPE + ";", "") + "@interface Repository {}\n"
        );
        sources.forEach(
            source -> assertTrue(
                inspect(directory, source, true).stream().anyMatch(
                    finding -> finding.is(DEMANDED)
                )
            )
        );
    }

    @Test
    void leavesInterfaceAndSuperclassChoicesUnchanged(@TempDir Path directory) {
        List<String> sources = List.of(
            REPOSITORY.replace(DECLARATION, DECLARATION + " implements Lookup"),
            REPOSITORY.replace(DECLARATION, DECLARATION + " extends Parent")
        );
        sources.forEach(
            source -> assertTrue(
                inspect(directory, source, true).stream().anyMatch(
                    finding -> finding.is(DEMANDED)
                )
            )
        );
    }

    @Test
    void leavesInheritedEnclosingScopesUnchanged(@TempDir Path directory) {
        String nested = REPOSITORY.replace(
            "@Repository\nclass Catalogue", "final class Envelope implements Lookup {\n@Repository\nclass Catalogue"
        ) + "}\n";
        assertTrue(inspect(directory, nested, true).stream().anyMatch(finding -> finding.is(DEMANDED)));
    }

    @Test
    void doesNotRelaxAnEnclosingOrNestedClass(@TempDir Path directory) {
        String enclosing = REPOSITORY.replace("@Repository\nclass Catalogue", "class Catalogue")
            .replace("    String read", "    @Repository class Nested {}\n    String read");
        String nested = REPOSITORY.replace("    String read", "    class Nested {}\n    String read");
        assertEquals(1, inspect(directory, enclosing, true).stream().filter(finding -> finding.is(DEMANDED)).count());
        assertEquals(1, inspect(directory, nested, true).stream().filter(finding -> finding.is(DEMANDED)).count());
    }

    @Test
    void leavesFinalRepositoriesToRuntimeProxyChecks(@TempDir Path directory) {
        List<Finding> findings = inspect(directory, REPOSITORY.replace(DECLARATION, "final " + DECLARATION), true);
        assertFalse(findings.stream().anyMatch(finding -> finding.is(DEMANDED)));
        assertFalse(findings.stream().anyMatch(finding -> finding.is("AirnessSpringProxiedClassIsNotFinal")));
    }

    private static List<Finding> inspect(Path directory, String source, boolean spring) {
        return CheckstyleConfigurationTest.inspect(
            directory, new Fixture("Catalogue.java", source, DEMANDED, 4), spring
        );
    }
}
