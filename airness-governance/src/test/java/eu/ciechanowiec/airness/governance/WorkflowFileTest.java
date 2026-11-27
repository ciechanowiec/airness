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
    private static final int REUSABLE_LINE = 12;
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
    void readsEveryActionAWorkflowUsesInEverySpelling() {
        String yaml = """
            jobs:
              build:
                steps:
                  - uses: actions/checkout@abc # v4.2.2
                  - name: Java
                    uses: 'actions/setup-java@v4'
                  - uses: "./.github/actions/install-maven"
                  # - uses: actions/cache@v3
                  - { uses: actions/cache@v4, with: { key: x } }
                  - {name: up, uses: 'actions/upload-artifact@v4'}
              call:
                uses: octo/flows/.github/workflows/build.yml@v1
            """;
        assertEquals(
            List.of(
                new Located<>(4, "actions/checkout@abc"), new Located<>(6, "actions/setup-java@v4"),
                new Located<>(7, "./.github/actions/install-maven"), new Located<>(9, "actions/cache@v4"),
                new Located<>(10, "actions/upload-artifact@v4"),
                new Located<>(REUSABLE_LINE, "octo/flows/.github/workflows/build.yml@v1")
            ),
            WorkflowFile.actions(yaml)
        );
    }

    @Test
    void readsNoActionFromAKeyThatOnlyEndsInUses() {
        assertEquals(List.of(), WorkflowFile.actions("steps:\n  - run: echo\n    causes: a/b@c\n    xuses: a/b@c\n"));
    }

    @Test
    void ignoresACommentedLineAndKeepsAnExpression() {
        String yaml = "# image: mongo:7\nsteps:\n  - uses: docker://${{ matrix.image }}\n";
        assertEquals(List.of("${{ matrix.image }}"), WorkflowFile.images(yaml).stream().map(Located::value).toList());
    }
}
