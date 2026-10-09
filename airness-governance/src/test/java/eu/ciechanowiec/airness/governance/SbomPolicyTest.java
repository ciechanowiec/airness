package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SbomPolicyTest {

    @TempDir
    private Path directory;

    @Test
    @SneakyThrows
    void rejectsSbomBypassesInsideInactiveProfiles() {
        for (String property : List.of("cyclonedx.skip", "cyclonedx.skipAttach", "cyclonedx.skipNotDeployed")) {
            String pom = """
                <project><profiles><profile><properties>
                    <%s>true</%s>
                </properties></profile></profiles></project>
                """.formatted(property, property);
            Path path = Files.writeString(this.directory.resolve("pom.xml"), pom);
            assertEquals(
                List.of("Remove child property " + property + "; it can bypass the Airness verdict"),
                MavenModelPolicy.problems(path)
            );
        }
    }
}
