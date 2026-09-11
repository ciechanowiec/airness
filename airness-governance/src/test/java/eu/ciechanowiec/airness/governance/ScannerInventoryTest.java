package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class ScannerInventoryTest {

    @Test
    void includesNewScriptsAndIgnoresBuildOutput() {
        Path root = new GitFixture("scanner-inputs")
            .write(".gitignore", "target/\n")
            .write("scripts/first.sh", "#!/bin/sh\nprintf '%s\\n' ready\n")
            .write("bin/second", "#!/usr/bin/env bash\nprintf '%s\\n' ready\n")
            .write("library.ksh", "print ready\n")
            .write("target/ignored.sh", "exit 1\n")
            .write("notes.md", "Prose is not a script.\n")
            .root();
        ScannerInventory inventory = new ScannerInventory(root);
        assertEquals(
            List.of("bin/second", "library.ksh", "scripts/first.sh"),
            inventory.scripts().stream().map(root::relativize).map(Path::toString).toList()
        );
        assertEquals(List.of(), inventory.problems());
    }

    @Test
    void rejectsNativeOverridesAndSourceSuppressions() {
        Path root = new GitFixture("scanner-bypasses")
            .write("nested/.checkov.yml", "soft-fail: true\n")
            .write("script.sh", "#!/bin/sh\n# shellcheck disable=SC2086\necho $value\n")
            .write("main.tf", "# checkov:skip=CKV_AWS_20: pretend this is safe\n")
            .write("pod.yaml", "metadata:\n annotations:\n  checkov.io/skip1: hidden\n")
            .root();
        List<String> problems = new ScannerInventory(root).problems();
        assertEquals(4, problems.size(), problems.toString());
        assertTrue(problems.stream().allMatch(problem -> problem.contains("Airness")));
    }

    @Test
    void leavesQuotedDocumentationAndNonShellSourcesAlone() {
        Path root = new GitFixture("scanner-quotes")
            .write("README.md", "# shellcheck disable=all\n")
            .write("Fixture.java", "# checkov:skip=CKV_AWS_20\n")
            .write("run.sh", "#!/bin/sh\n# shellcheck shell=sh\nprintf '%s\\n' ready\n")
            .root();
        assertEquals(List.of(), new ScannerInventory(root).problems());
    }

    @Test
    @SneakyThrows
    void rejectsLinksRatherThanFollowingUnselectedInput() {
        Path root = new GitFixture("scanner-link").write("real.sh", "#!/bin/sh\nexit 0\n").root();
        Files.createSymbolicLink(root.resolve("link.sh"), Path.of("real.sh"));
        assertTrue(new ScannerInventory(root).problems().getFirst().contains("symbolic links"));
    }
}
