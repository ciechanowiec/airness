package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A pnpm command runs in its directory with the harness's Node and pnpm first on the search path, its output
 * lands in its log, and a command that outlives the timeout is stopped and fails.
 */
class NodeToolsTest {

    private static final Duration GENEROUS = Duration.ofSeconds(30);
    private static final Duration BRIEF = Duration.ofMillis(200);

    @TempDir
    private Path directory;

    @SneakyThrows
    private Path executable(String name, String script) {
        Path file = Files.writeString(this.directory.resolve(name), script);
        Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rwxr-xr-x"));
        return file;
    }

    @Test
    @SneakyThrows
    void runsPnpmInItsDirectoryWithTheHarnessToolsFirstOnThePath() {
        Path pnpm = this.executable(
            "pnpm",
            "#!/bin/sh\nprintf '%s|%s|%s|%s\\n' \"$*\" \"$(pwd -P)\" \"$PATH\" "
                + "\"$npm_config_manage_package_manager_versions\"\nexit 3\n"
        );
        Path node = this.directory.resolve("node-dist/bin/node");
        Path log = this.directory.resolve("logs/run.log");
        int exit = new NodeTools(node, pnpm, GENEROUS).pnpm(List.of("run", "build"), this.directory, log);
        assertEquals(3, exit, "the exit code is passed back unchanged");
        String[] recorded = Files.readString(log).strip().split("\\|", -1);
        assertEquals("run build", recorded[0]);
        assertEquals(this.directory.toRealPath().toString(), recorded[1]);
        assertTrue(
            recorded[2].startsWith(node.getParent() + File.pathSeparator + this.directory + File.pathSeparator),
            "Node comes first, then pnpm, then what the build inherited: " + recorded[2]
        );
        assertEquals("false", recorded[3], "pnpm may not switch to a version a user configuration names");
    }

    @Test
    @SneakyThrows
    void stopsACommandThatOutlivesTheTimeout() {
        Path pnpm = this.executable("pnpm", "#!/bin/sh\nsleep 30\n");
        NodeTools tools = new NodeTools(pnpm, pnpm, BRIEF);
        Path log = this.directory.resolve("slow.log");
        IOException refusal = assertThrows(
            IOException.class, () -> tools.pnpm(List.of("install"), this.directory, log)
        );
        assertTrue(String.valueOf(refusal.getMessage()).contains("ran past"), refusal.getMessage());
    }

    @Test
    void addsTheHarnessToolsInFrontOfAnEmptyOrInheritedPath() {
        NodeTools tools = NodeTools.of(Path.of("cache/node/bin/node"), Path.of("cache/pnpm/pnpm"));
        String harness = Path.of("cache/node/bin") + File.pathSeparator + Path.of("cache/pnpm");
        assertEquals(harness, tools.path(""));
        String inherited = String.join(File.pathSeparator, "bin", "tools");
        assertEquals(harness + File.pathSeparator + inherited, tools.path(inherited));
    }
}
