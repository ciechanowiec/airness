package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.CheckstyleConfigurationTest.Fixture;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AsciidoctorInheritanceCheckstyleTest {

    @Test
    void permitsTheAsciidoctorTreeExtensionPoint(@TempDir Path directory) {
        Fixture fixture = new Fixture(
            "Sample.java", "final class Sample extends %s {}".formatted("Treeprocessor"),
            "Inheritance is allowed", 1
        );
        assertTrue(
            CheckstyleConfigurationTest.inspect(directory, fixture, false).stream()
                .noneMatch(finding -> finding.matches(fixture)),
            "AsciidoctorJ registers a Java tree extension through its abstract Treeprocessor base"
        );
    }

    @Test
    void refusesUnrelatedTreeProcessorBases(@TempDir Path directory) {
        List<Fixture> fixtures = List.of(
            new Fixture(
                "Sample.java", "final class Sample extends %s {}".formatted("CustomTreeprocessor"),
                "Inheritance is allowed", 1
            ),
            new Fixture(
                "Sample.java", "final class Sample extends %s {}".formatted("TreeprocessorExtension"),
                "Inheritance is allowed", 1
            ),
            new Fixture(
                "Sample.java", "final class Sample extends %s {}".formatted("CustomBase"),
                "Inheritance is allowed", 1
            )
        );
        for (Fixture fixture : fixtures) {
            assertTrue(
                CheckstyleConfigurationTest.inspect(directory, fixture, false).stream()
                    .anyMatch(finding -> finding.matches(fixture)),
                () -> "the Treeprocessor exception must not allow " + fixture.source()
            );
        }
    }
}
