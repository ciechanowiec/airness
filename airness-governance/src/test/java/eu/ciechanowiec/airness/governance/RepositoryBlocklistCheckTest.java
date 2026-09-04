package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The repository half of the blocklist reads every file that names an image or a JDK, and the JDK the
 * build runs on, and names each refusal by file and line.
 */
class RepositoryBlocklistCheckTest {

    private static final String OPEN_RUNTIME = "OpenJDK Runtime Environment";
    private static final String HEADLINE = "repository names";
    private static final String WORKFLOW = """
        jobs:
          build:
            services:
              db:
                image: mongo:7.0.14
            steps:
              - uses: actions/setup-java@v4
                with:
                  distribution: oracle
        """;
    private static final String EXTENSIONS = """
        <extensions>
            <extension>
                <groupId>com.gradle</groupId>
                <artifactId>develocity-maven-extension</artifactId>
                <version>1.23</version>
            </extension>
        </extensions>
        """;

    private static List<String> offences(Path root, String runtime) {
        return Verdicts.offences(new RepositoryBlocklistCheck(root, runtime).findings(), HEADLINE);
    }

    @Test
    void passesARepositoryThatNamesNothingRefused() {
        Path root = new GitFixture("repository-clean")
            .write("Dockerfile", "FROM eclipse-temurin:25-jre\nRUN apt-get install -y curl\n")
            .write("compose.yaml", "services:\n  db:\n    image: postgres:18\n")
            .root();
        RepositoryBlocklistCheck check = new RepositoryBlocklistCheck(root, OPEN_RUNTIME);
        assertTrue(Verdicts.clean(check.findings()), "an open image, an open package, and an open JDK");
        assertEquals(3, check.scanned(), "two files and the running JDK");
    }

    @Test
    void refusesWhatADockerfileNamesByLine() {
        Path root = new GitFixture("repository-dockerfile")
            .write("docker/Dockerfile", "FROM redis:7.4.1\nRUN apt-get install -y ghostscript\n")
            .root();
        List<String> offences = offences(root, OPEN_RUNTIME);
        assertEquals(2, offences.size(), offences.toString());
        assertTrue(offences.getFirst().startsWith("docker/Dockerfile:1: redis:7.4.1 - "), offences.getFirst());
        assertTrue(offences.getLast().startsWith("docker/Dockerfile:2: ghostscript - "), offences.getLast());
    }

    @Test
    void refusesWhatAComposeFileNamesWithItsService() {
        Path root = new GitFixture("repository-compose")
            .write("compose.yaml", "services:\n  store:\n    image: mongo:7.0.14\n  cache:\n    image: ${CACHE}\n")
            .root();
        List<String> offences = offences(root, OPEN_RUNTIME);
        assertEquals(2, offences.size(), offences.toString());
        assertTrue(offences.getFirst().startsWith("compose.yaml:3 (service store): mongo:7.0.14 - "));
        assertTrue(offences.getLast().contains("(service cache): ${CACHE} - a variable nothing substituted"));
    }

    @Test
    void refusesWhatAWorkflowNames() {
        Path root = new GitFixture("repository-workflow").write(".github/workflows/build.yml", WORKFLOW).root();
        List<String> offences = offences(root, OPEN_RUNTIME);
        assertEquals(2, offences.size(), offences.toString());
        assertTrue(offences.getFirst().startsWith(".github/workflows/build.yml:5: mongo:7.0.14 - "));
        assertTrue(offences.getLast().startsWith(".github/workflows/build.yml:9: oracle - Oracle JDK"));
    }

    @Test
    void refusesACoreExtensionAndASdkmanVendor() {
        Path root = new GitFixture("repository-tooling")
            .write(".mvn/extensions.xml", EXTENSIONS)
            .write(".sdkmanrc", "# jdk\njava=25\njava=25.0.1-oracle\nmaven=3.9.16\n")
            .root();
        List<String> offences = offences(root, OPEN_RUNTIME);
        assertEquals(2, offences.size(), offences.toString());
        assertTrue(
            offences.getFirst().startsWith(".mvn/extensions.xml: com.gradle:develocity-maven-extension:1.23 - ")
        );
        assertTrue(
            offences.getLast().startsWith(".sdkmanrc:3: oracle - "), "a bare version names no vendor: " + offences
        );
    }

    @Test
    void refusesTheJdkTheBuildRunsOnWhenItIsNotAnOpenBuild() {
        Path root = new GitFixture("repository-runtime").write("README.md", "Nothing here.\n").root();
        List<String> offences = offences(root, "Java(TM) SE Runtime Environment");
        assertEquals(1, offences.size());
        assertTrue(offences.getFirst().startsWith("the build JDK: Java(TM) SE Runtime Environment - "));
        assertEquals(1, new RepositoryBlocklistCheck(root, OPEN_RUNTIME).scanned(), "only the JDK was there to read");
    }

    @Test
    void leavesAnIgnoredDockerfileUnread() {
        Path root = new GitFixture("repository-ignored")
            .write(".gitignore", "build/\n")
            .write("build/Dockerfile", "FROM mongo:7\n")
            .root();
        assertTrue(
            Verdicts.clean(new RepositoryBlocklistCheck(root, OPEN_RUNTIME).findings()), "build output is not content"
        );
    }
}
