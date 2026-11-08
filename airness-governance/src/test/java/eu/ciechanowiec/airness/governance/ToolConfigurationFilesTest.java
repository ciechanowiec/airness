package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolConfigurationFilesTest {

    @Test
    void refusesASecretScanIgnoreFileEvenWhenGitIgnoresIt() {
        Path root = new GitFixture("tool-files-gitleaks")
            .write(".gitignore", ".gitleaksignore\n")
            .write(".gitleaksignore", "0123abcd:config.properties:generic-api-key:3\n")
            .root();
        assertEquals(
            List.of(
                ".gitleaksignore: gitleaks drops every finding this file lists; write the exception in "
                    + ".gitleaks.toml, naming the rule and the exact value"
            ),
            offences(root),
            "gitleaks reads the file from the scanned tree on disk, so neither git nor a flag keeps it out"
        );
    }

    @Test
    void refusesTheTimeoutKeysOfJunitButLeavesTheRestOfItsFile() {
        Path root = new GitFixture("tool-files-junit")
            .write(
                "service/src/test/resources/junit-platform.properties",
                """
                    # junit.jupiter.execution.timeout.mode = disabled is how it would be switched off
                    junit.jupiter.extensions.autodetection.enabled = true
                    junit.jupiter.execution.timeout.mode = disabled
                    junit.jupiter.execution.timeout.test.method.default=1 h
                    """
            ).root();
        assertEquals(
            List.of(
                "service/src/test/resources/junit-platform.properties: the harness pins "
                    + "junit.jupiter.execution.timeout.mode; remove the key",
                "service/src/test/resources/junit-platform.properties: the harness pins "
                    + "junit.jupiter.execution.timeout.test.method.default; remove the key"
            ),
            offences(root),
            "extension detection is the project's to set, the timeout keys are not, and a comment sets nothing"
        );
    }

    @Test
    void refusesANativeScannerConfigurationOnEveryBuild() {
        Path root = new GitFixture("tool-files-scanners")
            .write("deploy/.checkov.yaml", "soft-fail: true\n")
            .write(".github/actionlint.yml", "paths: {}\n")
            .write(".github/workflows/actionlint.yml", "on: push\njobs: {}\n")
            .root();
        assertEquals(
            List.of(
                ".github/actionlint.yml: scanner configuration belongs to Airness; remove the local copy",
                "deploy/.checkov.yaml: scanner configuration belongs to Airness; remove the local copy"
            ),
            offences(root),
            "a workflow named like the actionlint configuration is a workflow"
        );
    }

    @Test
    void namesTheScannerConfigurationsByWhereTheScannerLooks() {
        assertAll(
            () -> assertTrue(ToolConfigurationFiles.scannerConfiguration(Path.of("a", "b", ".shellcheckrc"))),
            () -> assertTrue(ToolConfigurationFiles.scannerConfiguration(Path.of(".github", "actionlint.yaml"))),
            () -> assertFalse(ToolConfigurationFiles.scannerConfiguration(Path.of("docs", "actionlint.yaml"))),
            () -> assertFalse(
                ToolConfigurationFiles.scannerConfiguration(Path.of("").toAbsolutePath().getRoot()),
                "a root has no name to read"
            )
        );
    }

    @Test
    void passesATreeHoldingNothingAToolWouldReadOnItsOwn() {
        Path root = new GitFixture("tool-files-clean")
            .write(".gitleaks.toml", "[extend]\nuseDefault = true\n")
            .write(
                "src/test/resources/junit-platform.properties", "junit.jupiter.extensions.autodetection.enabled=true\n"
            ).root();
        assertEquals(List.of(), offences(root));
    }

    private static List<String> offences(Path root) {
        return new ToolConfigurationFiles(root).findings().getFirst().offences();
    }
}
