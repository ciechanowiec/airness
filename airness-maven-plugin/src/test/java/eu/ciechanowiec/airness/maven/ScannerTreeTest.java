package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.Findings;
import eu.ciechanowiec.airness.governance.ScannerInventory;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScannerTreeTest {

    private static final String CACHE = "cache";

    @Test
    @SneakyThrows
    void copiesTheCommittableBytesIntoSeparateInputsAndOutputs(@TempDir Path directory) {
        Path root = repository(directory);
        Files.writeString(root.resolve("script with spaces.sh"), "#!/bin/sh\nexit 0\n");
        ScannerTree tree = ScannerTree.create(directory.resolve(CACHE), "shellcheck", new ScannerInventory(root));
        assertEquals("#!/bin/sh\nexit 0\n", Files.readString(tree.input().resolve("script with spaces.sh")));
        assertEquals("{}\n", Files.readString(tree.directory().resolve("empty.yaml")));
        assertFalse(tree.input().startsWith(tree.directory()));
        assertEquals(directory.resolve(CACHE), tree.location().getParent());
        assertTrue(tree.location().getFileName().toString().startsWith("shellcheck-"));
    }

    @Test
    @SneakyThrows
    void refusesOverridesBeforePreparingScannerOutput(@TempDir Path directory) {
        Path root = repository(directory);
        Files.writeString(root.resolve(".checkov.yaml"), "soft-fail: true\n");
        assertThrows(
            IOException.class,
            () -> ScannerTree.create(directory.resolve(CACHE), "checkov", new ScannerInventory(root))
        );
        assertFalse(Files.exists(directory.resolve(CACHE)));
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
        Path cache = directory.resolve(CACHE);
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
    @SneakyThrows
    void prunesEveryKeptTreeOlderThanADayWhicheverToolKeptIt(@TempDir Path directory) {
        Path cache = directory.resolve(CACHE);
        Instant now = Instant.parse("2100-01-01T12:00:00Z");
        ScannerTree stale = aged(ScannerTree.empty(cache, "checkov"), now.minus(ScannerTree.KEPT).minusSeconds(1));
        ScannerTree nested = ScannerTree.empty(stale.directory(), "terraform");
        Files.writeString(nested.input().resolve("main.tf"), "resource {}\n");
        List<ScannerTree> removed = List.of(stale, aged(ScannerTree.empty(cache, "shellcheck"), Instant.EPOCH));
        List<ScannerTree> kept = List.of(
            aged(ScannerTree.empty(cache, "checkov"), now.minus(ScannerTree.KEPT).plusSeconds(1)),
            aged(ScannerTree.empty(cache, "actionlint"), now)
        );
        ScannerTree.prune(cache, Clock.fixed(now, ZoneOffset.UTC));
        assertTrue(
            removed.stream().noneMatch(tree -> Files.exists(tree.location())),
            "a tree kept longer than a day is removed, nested trees and all, whichever tool kept it"
        );
        assertTrue(
            kept.stream().allMatch(tree -> Files.isDirectory(tree.input())),
            "a tree younger than a day is still evidence, and a scan running beside this one keeps its tree"
        );
    }

    @Test
    @SneakyThrows
    void startsEveryScanByPruningTheCache(@TempDir Path directory) {
        Path root = repository(directory);
        Path cache = directory.resolve(CACHE);
        ScannerTree old = aged(ScannerTree.empty(cache, "docker"), Instant.parse("2000-01-01T00:00:00Z"));
        ScannerTree tree = ScannerTree.create(cache, "shellcheck", new ScannerInventory(root));
        assertFalse(Files.exists(old.location()), "a tree an earlier run kept is gone once a new scan starts");
        assertTrue(Files.isDirectory(tree.input()));
    }

    @Test
    @SneakyThrows
    void prunesNothingWhereNoScanEverRan(@TempDir Path directory) {
        Path cache = directory.resolve(CACHE);
        ScannerTree.prune(cache, Clock.fixed(Instant.parse("2100-01-01T12:00:00Z"), ZoneOffset.UTC));
        assertFalse(Files.exists(cache), "pruning creates no cache");
    }

    @Test
    @SneakyThrows
    void passesOverATreeAnotherScanRemovedFirst(@TempDir Path directory) {
        Path gone = directory.resolve("gone");
        ScannerTree.remove(gone);
        assertFalse(Files.exists(gone), "a tree already removed is removed");
        assertDoesNotThrow(() -> ScannerTree.passOver(null), "nothing raised lets the removal continue");
        assertDoesNotThrow(
            () -> ScannerTree.passOver(new NoSuchFileException("gone")), "a path already gone lets the removal continue"
        );
        AccessDeniedException denied = new AccessDeniedException("kept");
        assertEquals(denied, assertThrows(IOException.class, () -> ScannerTree.passOver(denied)));
    }

    @Test
    @SneakyThrows
    void failsAndNamesAnOldTreeItCannotRemove(@TempDir Path directory) {
        ScannerTree tree = ScannerTree.empty(directory.resolve(CACHE), "docker");
        Path output = tree.directory();
        Set<PosixFilePermission> writable = Files.getPosixFilePermissions(output);
        Files.setPosixFilePermissions(output, Set.of());
        try {
            IOException failure = assertThrows(IOException.class, () -> ScannerTree.remove(tree.location()));
            assertEquals(
                "Could not remove the scanner tree " + tree.location()
                    + ", which an earlier scan kept; delete it by hand",
                failure.getMessage()
            );
        } finally {
            Files.setPosixFilePermissions(output, writable);
        }
    }

    @SneakyThrows
    private static ScannerTree aged(ScannerTree tree, Instant time) {
        Files.setLastModifiedTime(tree.location(), FileTime.from(time));
        return tree;
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
