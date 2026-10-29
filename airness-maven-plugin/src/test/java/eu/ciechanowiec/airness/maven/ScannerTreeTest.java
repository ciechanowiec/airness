package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.ScannerInventory;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScannerTreeTest {

    @Test
    @SneakyThrows
    void copiesTheCommittableBytesIntoSeparateInputsAndOutputs(@TempDir Path directory) {
        Path root = repository(directory);
        Files.writeString(root.resolve("script with spaces.sh"), "#!/bin/sh\nexit 0\n");
        ScannerTree tree = ScannerTree.create(directory.resolve("cache"), "shellcheck", new ScannerInventory(root));
        assertEquals("#!/bin/sh\nexit 0\n", Files.readString(tree.input().resolve("script with spaces.sh")));
        assertEquals("{}\n", Files.readString(tree.directory().resolve("empty.yaml")));
        assertFalse(tree.input().startsWith(tree.directory()));
        assertEquals(directory.resolve("cache"), tree.location().getParent());
        assertTrue(tree.location().getFileName().toString().startsWith("shellcheck-"));
    }

    @Test
    @SneakyThrows
    void refusesOverridesBeforePreparingScannerOutput(@TempDir Path directory) {
        Path root = repository(directory);
        Files.writeString(root.resolve(".checkov.yaml"), "soft-fail: true\n");
        assertThrows(
            IOException.class,
            () -> ScannerTree.create(directory.resolve("cache"), "checkov", new ScannerInventory(root))
        );
        assertFalse(Files.exists(directory.resolve("cache")));
    }

    @Test
    void refusesAnInputThatDisappearedAfterDiscovery(@TempDir Path directory) {
        assertThrows(
            IOException.class, () -> ScannerTree.copy(directory.resolve("missing"), directory.resolve("output"))
        );
    }

    @Test
    @SneakyThrows
    void removesAPartialTreeWhenAnInputCannotBeStaged(@TempDir Path directory) {
        Path root = repository(directory);
        Path source = Files.writeString(root.resolve(".airness-scanner-input"), "collides with the tree marker\n");
        Path cache = directory.resolve("cache");
        IOException failure = assertThrows(
            IOException.class, () -> ScannerTree.create(cache, "shellcheck", new ScannerInventory(root))
        );
        assertEquals("Could not stage the scanner input " + source, failure.getMessage());
        assertInstanceOf(FileAlreadyExistsException.class, failure.getCause());
        try (Stream<Path> left = Files.list(cache)) {
            assertEquals(List.of(), left.toList());
        }
    }

    @Test
    @SneakyThrows
    void deletesTheTreeAfterAPassingScan(@TempDir Path directory) {
        ScannerTree tree = ScannerTree.empty(directory, "checkov");
        Files.createDirectory(ScannerTree.empty(tree.directory(), "terraform").input().resolve(".git"));
        List<String> evidence = new ArrayList<>();
        List<Findings> verdicts = tree.conclude(evidence::add, () -> List.of(new Findings("Checkov", List.of())));
        assertTrue(verdicts.getFirst().clean());
        assertFalse(Files.exists(tree.location()));
        assertEquals(List.of(), evidence);
    }

    @Test
    @SneakyThrows
    void keepsAndNamesTheTreeAfterAFinding(@TempDir Path directory) {
        ScannerTree tree = ScannerTree.empty(directory, "checkov");
        List<String> evidence = new ArrayList<>();
        List<Findings> verdicts = tree.conclude(
            evidence::add,
            () -> List.of(new Findings("Clean", List.of()), new Findings("Checkov", List.of("main.tf:1 CKV_AWS_1")))
        );
        assertFalse(verdicts.getLast().clean());
        assertTrue(Files.isRegularFile(tree.directory().resolve("empty.yaml")));
        assertEquals(List.of("Scanner evidence: " + tree.location()), evidence);
    }

    @Test
    @SneakyThrows
    void keepsAndNamesTheTreeAfterAFailedScan(@TempDir Path directory) {
        ScannerTree tree = ScannerTree.empty(directory, "actionlint");
        List<String> evidence = new ArrayList<>();
        IOException failure = assertThrows(
            IOException.class, () -> tree.conclude(evidence::add, ScannerTreeTest::unreachableDaemon)
        );
        assertEquals("Docker is installed but its daemon is not reachable", failure.getMessage());
        assertTrue(Files.isDirectory(tree.input()));
        assertEquals(List.of("Scanner evidence: " + tree.location()), evidence);
    }

    @Test
    void placesTheScannerCacheBeneathTheHomeDirectory() {
        Path home = Path.of(System.getProperty("user.home"));
        assertEquals(home.resolve(".cache").resolve("airness").resolve("scan"), ScannerTree.cache());
    }

    @SneakyThrows
    private static List<Findings> unreachableDaemon() {
        throw new IOException("Docker is installed but its daemon is not reachable");
    }

    @SneakyThrows
    private static Path repository(Path directory) {
        Path root = Files.createDirectory(directory.resolve("repository"));
        ScannerCommand.run(List.of("git", "init", "--quiet", root.toString()), directory.resolve("git.txt"));
        return root;
    }
}
