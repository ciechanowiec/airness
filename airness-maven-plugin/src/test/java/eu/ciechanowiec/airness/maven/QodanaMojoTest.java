package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
                directory.resolve("profile.xml"),
                new QodanaPaths.Environment(directory.resolve("roots.pem"), localRepository), List.of()
            ),
            "qodana:test"
        );
        assertTrue(command.contains(localRepository + ":/opt/maven-repository:ro"));
        assertTrue(command.getLast().contains(QodanaSnapshot.SCRIPT));
        assertTrue(command.getLast().contains("cp -as"));
        assertTrue(command.getLast().contains("_remote.repositories"));
        assertTrue(command.getLast().startsWith("set -- /opt/project /data/project /data/results/source-files; "));
    }

    @Test
    void mountsALinkedWorktreesGitDirectoriesAndHandsThemToTheCopy(@TempDir Path directory) {
        List<String> metadata = List.of("-v", "/own:" + QodanaSnapshot.OWN + ":ro", "-v", "/common:/c:ro");
        List<String> command = QodanaMojo.dockerCommand(
            new QodanaPaths(
                directory.resolve("project"), directory.resolve("output"), directory.resolve("profile.xml"),
                new QodanaPaths.Environment(directory.resolve("roots.pem"), directory.resolve("repository")), metadata
            ),
            "qodana:test"
        );
        int entrypoint = command.indexOf("--entrypoint");
        assertEquals(metadata, command.subList(entrypoint - metadata.size(), entrypoint), "mounted read-only");
        assertTrue(
            command.getLast().startsWith(
                "set -- /opt/project /data/project /data/results/source-files " + QodanaSnapshot.OWN + " "
                    + QodanaSnapshot.COMMON + "; "
            ),
            "the copy is made from the worktree's own and common Git directories"
        );
    }
}
