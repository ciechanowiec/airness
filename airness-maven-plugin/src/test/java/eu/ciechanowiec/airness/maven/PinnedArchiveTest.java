package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.maven.ArchiveServer.Entry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A pinned archive is installed once, only when its bytes match the pin, and only inside its own directory.
 */
class PinnedArchiveTest {

    private static final String PATH = "/tool.tgz";
    private static final String NAME = "tool-1.0.0";
    private static final String WRONG_HASH = "0".repeat(64);
    private static final List<Entry> TOOL = List.of(
        Entry.directory("tool-1.0.0/"),
        Entry.directory("tool-1.0.0/bin/"),
        Entry.executable("tool-1.0.0/bin/tool", "#!/bin/sh\necho tool\n"),
        Entry.file("tool-1.0.0/README", "read me\n"),
        Entry.symbolicLink("tool-1.0.0/bin/alias", "tool"),
        Entry.file("pax_global_header", "ignored at the root\n")
    );

    @TempDir
    private Path cache;

    private final ArchiveServer server;

    @SneakyThrows
    PinnedArchiveTest() {
        this.server = new ArchiveServer();
    }

    @AfterEach
    void stopServer() {
        this.server.close();
    }

    private PinnedArchive pinned(List<Entry> entries) {
        byte[] archive = ArchiveServer.archive(entries);
        return new PinnedArchive(NAME, this.server.serve(PATH, archive), ArchiveServer.sha256(archive));
    }

    @Test
    @SneakyThrows
    void unpacksBelowTheTopDirectoryKeepingExecutablesAndLinks() {
        Path unpacked = this.pinned(TOOL).unpackedIn(this.cache);
        assertEquals(this.cache.resolve(NAME), unpacked);
        assertTrue(Files.isExecutable(unpacked.resolve("bin/tool")), "the executable keeps its mode");
        assertFalse(Files.isExecutable(unpacked.resolve("README")), "a plain file is not made executable");
        assertEquals(Path.of("tool"), Files.readSymbolicLink(unpacked.resolve("bin/alias")));
        assertEquals(
            List.of(NAME), names(this.cache),
            "neither the root-level entry nor a staging directory is left in the cache"
        );
    }

    @Test
    @SneakyThrows
    void reusesAnInstalledArchiveWithoutDownloadingAgain() {
        PinnedArchive archive = this.pinned(TOOL);
        archive.unpackedIn(this.cache);
        int downloads = this.server.requests();
        archive.unpackedIn(this.cache);
        assertEquals(downloads, this.server.requests(), "the second build finds the archive in the cache");
    }

    @Test
    void refusesBytesThatDoNotMatchThePin() {
        byte[] archive = ArchiveServer.archive(TOOL);
        PinnedArchive tampered = new PinnedArchive(NAME, this.server.serve(PATH, archive), WRONG_HASH);
        IOException refusal = assertThrows(IOException.class, () -> tampered.unpackedIn(this.cache));
        assertTrue(Objects.requireNonNull(refusal.getMessage()).contains("Airness pins"), refusal.getMessage());
        assertFalse(Files.exists(this.cache.resolve(NAME)), "nothing is installed from a mismatched download");
        assertEquals(List.of(), names(this.cache), "the staging directory is removed");
    }

    @Test
    void failsWhenTheSourceAnswersWithoutTheArchive() {
        PinnedArchive missing = new PinnedArchive(NAME, this.server.uri("/absent.tgz"), WRONG_HASH);
        IOException refusal = assertThrows(IOException.class, () -> missing.unpackedIn(this.cache));
        assertTrue(Objects.requireNonNull(refusal.getMessage()).contains("HTTP 404"), refusal.getMessage());
    }

    @Test
    void refusesAnEntryThatWouldLandOutsideTheDirectory() {
        PinnedArchive escaping = this.pinned(List.of(Entry.file("tool-1.0.0/../../escaped", "x\n")));
        assertThrows(IOException.class, () -> escaping.unpackedIn(this.cache));
        assertFalse(Files.exists(this.cache.resolve("escaped")));
    }

    @Test
    void refusesALinkPointingOutsideTheDirectory() {
        PinnedArchive escaping = this.pinned(List.of(Entry.symbolicLink("tool-1.0.0/link", "../../outside")));
        assertThrows(IOException.class, () -> escaping.unpackedIn(this.cache));
    }

    @Test
    void refusesAnEntryThatIsNeitherFileDirectoryNorSymbolicLink() {
        PinnedArchive hard = this.pinned(List.of(Entry.hardLink()));
        IOException refusal = assertThrows(IOException.class, () -> hard.unpackedIn(this.cache));
        assertTrue(Objects.requireNonNull(refusal.getMessage()).contains("tool-1.0.0/hard"), refusal.getMessage());
    }

    private static List<String> names(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries.map(entry -> entry.getFileName().toString()).sorted().toList();
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
