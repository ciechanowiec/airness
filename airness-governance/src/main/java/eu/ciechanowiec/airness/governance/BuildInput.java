package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Set;

/**
 * Files selected from one repository input directory by the build tool.
 *
 * @param directory the absolute input directory
 * @param kind      the module and purpose used in diagnostics
 * @param selected  relative file names selected by the build tool
 */
public record BuildInput(Path directory, String kind, Set<String> selected) {

    /**
     * Keeps a stable selection independent of the caller's collection.
     *
     * @param directory the input directory
     * @param kind      the diagnostic description
     * @param selected  selected relative file names
     */
    public BuildInput {
        directory = directory.toAbsolutePath().normalize();
        selected = Set.copyOf(selected);
    }
}
