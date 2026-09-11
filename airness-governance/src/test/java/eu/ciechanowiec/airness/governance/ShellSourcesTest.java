package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShellSourcesTest {

    @Test
    void checksLibrariesWithTheirCallerWhileRetainingIndependentScripts() {
        Path root = new GitFixture("shell-source-graph")
            .write("run.sh", ". \"${repository}/library.sh\"\n")
            .write("library.sh", "printf '%s\\n' \"${argument}\"\n")
            .write("independent.sh", "exit 0\n")
            .root();
        ScannerInventory inventory = new ScannerInventory(root);
        assertEquals(
            List.of(root.resolve("independent.sh"), root.resolve("run.sh")),
            ShellSources.entryPoints(root, inventory.scripts())
        );
    }

    @Test
    void neverTurnsACyclicSourceGraphIntoAnEmptyScan() {
        Path root = new GitFixture("shell-source-cycle")
            .write("first.sh", ". second.sh\n")
            .write("second.sh", "source first.sh\n")
            .root();
        ScannerInventory inventory = new ScannerInventory(root);
        assertEquals(List.of(root.resolve("first.sh")), ShellSources.entryPoints(root, inventory.scripts()));
    }
}
