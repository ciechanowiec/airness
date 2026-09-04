package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The workflow reader finds service containers, job containers in both spellings, Docker actions, and
 * the JDK distribution a setup step installs.
 */
class WorkflowFileTest {

    private static final List<Integer> IMAGE_LINES = List.of(4, 7, 9, 16);
    private static final String WORKFLOW = """
        jobs:
          build:
            runs-on: ubuntu-latest
            container: 'eclipse-temurin:25-jdk' # the build image
            services:
              db:
                image: postgres:18
            steps:
              - uses: docker://alpine:3.21
              - uses: actions/setup-java@v4
                with:
                  distribution: "temurin"
                  java-version: 25
          scan:
            container:
              image: mongo:7.0.14
        """;

    @Test
    void readsEveryImageAWorkflowPulls() {
        List<String> images = WorkflowFile.images(WORKFLOW).stream().map(Located::value).toList();
        assertEquals(List.of("eclipse-temurin:25-jdk", "postgres:18", "alpine:3.21", "mongo:7.0.14"), images);
        assertEquals(IMAGE_LINES, WorkflowFile.images(WORKFLOW).stream().map(Located::line).toList());
    }

    @Test
    void readsTheDistributionASetupStepInstalls() {
        assertEquals(List.of("temurin"), WorkflowFile.distributions(WORKFLOW).stream().map(Located::value).toList());
    }

    @Test
    void ignoresACommentedLineAndKeepsAnExpression() {
        String yaml = "# image: mongo:7\nsteps:\n  - uses: docker://${{ matrix.image }}\n";
        assertEquals(List.of("${{ matrix.image }}"), WorkflowFile.images(yaml).stream().map(Located::value).toList());
    }
}
