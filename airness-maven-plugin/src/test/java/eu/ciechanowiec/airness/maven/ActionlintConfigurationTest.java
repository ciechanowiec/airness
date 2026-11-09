package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;

import eu.ciechanowiec.airness.governance.RunnerImageCheck;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The harness's actionlint configuration declares the runner labels the runner rule requires, and nothing else.
 */
class ActionlintConfigurationTest {

    @Test
    void declaresEveryLabelTheRunnerRuleRequiresAndNothingElse() {
        String expected = RunnerImageCheck.currentLabels().stream()
            .reduce("self-hosted-runner:\n  labels:\n", (text, label) -> text + "    - " + label + "\n");
        assertEquals(expected, ActionlintConfiguration.text(), "a key beyond the labels could silence a finding");
    }

    @Test
    @SneakyThrows
    void writesTheConfigurationWhereActionlintReadsItEvenWithoutAGithubDirectory(@TempDir Path tree) {
        ActionlintConfiguration.writeInto(tree);
        assertEquals(
            ActionlintConfiguration.text(), Files.readString(tree.resolve(".github").resolve("actionlint.yaml")),
            "actionlint reads its configuration from the .github directory of the tree it lints"
        );
    }
}
