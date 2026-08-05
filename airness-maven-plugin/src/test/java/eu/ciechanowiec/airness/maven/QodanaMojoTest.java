package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QodanaMojoTest {

    @Test
    void mountsOnlyTheResolvedMavenRepositoryReadOnly(
        @TempDir Path directory
    ) {
        Path localRepository = directory.resolve("repository");
        List<String> command = QodanaMojo.dockerCommand(
            new QodanaPaths(
                directory.resolve("project"), directory.resolve("output"),
                directory.resolve("profile.xml"), directory.resolve("roots.pem"),
                localRepository
            ),
            "qodana:test"
        );
        assertTrue(command.contains(localRepository + ":/opt/maven-repository:ro"));
        assertTrue(command.getLast().contains("cp -as"));
        assertTrue(command.getLast().contains("_remote.repositories"));
    }
}
