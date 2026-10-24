package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.maven.ArchiveServer.Entry;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The toolchain is read from the pins the plugin ships, for the platform the build runs on, and installs
 * both executables from the sources the pins name.
 */
class NodeToolchainTest {

    private static final String PLATFORM = "linux-x64";

    @TempDir
    private Path cache;

    @Test
    void namesThePlatformTheWayTheReleaseArchivesDo() {
        assertEquals("darwin-arm64", NodeToolchain.platform("Mac OS X", "aarch64"));
        assertEquals("linux-x64", NodeToolchain.platform("Linux", "amd64"));
        IllegalStateException refusal = assertThrows(
            IllegalStateException.class, () -> NodeToolchain.platform("Windows 11", "amd64")
        );
        assertTrue(Objects.requireNonNull(refusal.getMessage()).contains("Windows 11 amd64"), refusal.getMessage());
        assertThrows(IllegalStateException.class, () -> NodeToolchain.platform("Linux", "s390x"));
    }

    @Test
    void shipsAPinForEveryArchiveOfEverySupportedPlatform() {
        NodeToolchain pinned = NodeToolchain.pinned();
        assertTrue(pinned.description().matches("Node \\d+\\.\\d+\\.\\d+ and pnpm \\d+\\.\\d+\\.\\d+"));
        for (String platform : List.of("darwin-arm64", "darwin-x64", "linux-arm64", "linux-x64")) {
            NodeToolchain toolchain = NodeToolchain.of(pinned.pins(), platform);
            assertTrue(toolchain.node().sha256().matches("[0-9a-f]{64}"), platform);
            assertTrue(toolchain.pnpm().sha256().matches("[0-9a-f]{64}"), platform);
            assertTrue(toolchain.node().source().toString().startsWith("https://nodejs.org/dist/v"), platform);
            assertTrue(toolchain.pnpm().source().toString().contains("/@pnpm/exe." + platform + "/-/"), platform);
        }
    }

    @Test
    void refusesAPlatformThePinsDoNotCover() {
        Properties pins = NodeToolchain.pinned().pins();
        IllegalStateException refusal = assertThrows(
            IllegalStateException.class, () -> NodeToolchain.of(pins, "freebsd-x64")
        );
        assertTrue(Objects.requireNonNull(refusal.getMessage()).contains("freebsd-x64"), refusal.getMessage());
        Properties partial = new Properties();
        partial.setProperty("node.version", "1.0.0");
        assertThrows(IllegalStateException.class, () -> NodeToolchain.of(partial, PLATFORM));
    }

    @Test
    void cachesBeneathTheLocalRepository() {
        assertEquals(
            Path.of("repository", ".airness", "toolchain"), NodeToolchain.cache(Path.of("repository"))
        );
    }

    @Test
    @SneakyThrows
    void installsBothExecutablesFromThePinnedSources() {
        byte[] node = ArchiveServer.archive(
            List.of(Entry.executable("node-v1.0.0-linux-x64/bin/node", "#!/bin/sh\necho v1.0.0\n"))
        );
        byte[] pnpm = ArchiveServer.archive(List.of(Entry.executable("package/pnpm", "#!/bin/sh\necho 2.0.0\n")));
        try (ArchiveServer server = new ArchiveServer()) {
            server.serve("/node/1.0.0/linux-x64.tgz", node);
            server.serve("/pnpm/2.0.0/linux-x64.tgz", pnpm);
            Properties pins = new Properties();
            pins.putAll(
                Map.of(
                    "node.version", "1.0.0", "node.executable", "bin/node",
                    "node.source", server.uri("/node/") + "{version}/{platform}.tgz",
                    "node.sha256.linux-x64", ArchiveServer.sha256(node),
                    "pnpm.version", "2.0.0", "pnpm.executable", "pnpm",
                    "pnpm.source", server.uri("/pnpm/") + "{version}/{platform}.tgz",
                    "pnpm.sha256.linux-x64", ArchiveServer.sha256(pnpm)
                )
            );
            NodeToolchain toolchain = NodeToolchain.of(pins, PLATFORM);
            NodeTools tools = toolchain.installIn(this.cache);
            assertEquals(this.cache.resolve("node-1.0.0-linux-x64/bin/node"), tools.node());
            assertEquals(this.cache.resolve("pnpm-2.0.0-linux-x64/pnpm"), tools.pnpm());
            assertTrue(Files.isExecutable(tools.node()) && Files.isExecutable(tools.pnpm()));
            assertEquals("Node 1.0.0 and pnpm 2.0.0", toolchain.description());
        }
    }
}
