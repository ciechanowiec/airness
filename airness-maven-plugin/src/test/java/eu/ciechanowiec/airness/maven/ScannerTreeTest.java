package eu.ciechanowiec.airness.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import eu.ciechanowiec.airness.governance.ScannerInventory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScannerTreeTest {

    @Test
    @SneakyThrows
    void copiesTheCommittableBytesIntoSeparateInputsAndOutputs(@TempDir Path directory) {
        Path root = Files.createDirectory(directory.resolve("repository"));
        ScannerCommand.run(List.of("git", "init", "--quiet", root.toString()), directory.resolve("git.txt"));
        Files.writeString(root.resolve("script with spaces.sh"), "#!/bin/sh\nexit 0\n");
        ScannerTree tree = ScannerTree.create(directory.resolve("build"), new ScannerInventory(root));
        assertEquals("#!/bin/sh\nexit 0\n", Files.readString(tree.input().resolve("script with spaces.sh")));
        assertEquals("{}\n", Files.readString(tree.directory().resolve("empty.yaml")));
        assertFalse(tree.input().startsWith(tree.directory()));
    }

    @Test
    @SneakyThrows
    void refusesOverridesBeforePreparingScannerOutput(@TempDir Path directory) {
        Path root = Files.createDirectory(directory.resolve("repository"));
        ScannerCommand.run(List.of("git", "init", "--quiet", root.toString()), directory.resolve("git.txt"));
        Files.writeString(root.resolve(".checkov.yaml"), "soft-fail: true\n");
        assertThrows(
            IOException.class, () -> ScannerTree.create(directory.resolve("build"), new ScannerInventory(root))
        );
        assertFalse(Files.exists(directory.resolve("build")));
    }

    @Test
    void refusesAnInputThatDisappearedAfterDiscovery(@TempDir Path directory) {
        assertThrows(
            IOException.class, () -> ScannerTree.copy(directory.resolve("missing"), directory.resolve("output"))
        );
    }
}
