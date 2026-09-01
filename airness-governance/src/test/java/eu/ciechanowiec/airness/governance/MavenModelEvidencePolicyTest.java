package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Holds the Maven-model protections specific to Spring context evidence.
 */
class MavenModelEvidencePolicyTest {

    @TempDir
    private Path directory;

    @Test
    void rejectsAChildDestinationForSpringContextEvidence() {
        String configuration = """
            <systemPropertyVariables>
                <airness.spring.context.evidence.file>elsewhere</airness.spring.context.evidence.file>
            </systemPropertyVariables>
            """;
        assertTrue(this.problems(configuration).getFirst().contains("airness.spring.context.evidence.file"));
    }

    @Test
    void rejectsANestedMergeOverrideThatDropsTheEvidenceDestination() {
        String configuration = """
            <systemPropertyVariables combine.self="override">
                <project.setting>value</project.setting>
            </systemPropertyVariables>
            """;
        assertTrue(this.problems(configuration).getFirst().contains("combine.self=override"));
    }

    @SneakyThrows
    private List<String> problems(String configuration) {
        String pom = """
            <project><build><plugins><plugin>
                <artifactId>maven-surefire-plugin</artifactId>
                <configuration>%s</configuration>
            </plugin></plugins></build></project>
            """.formatted(configuration);
        Path path = Files.writeString(this.directory.resolve("pom.xml"), pom);
        return MavenModelPolicy.problems(path);
    }
}
