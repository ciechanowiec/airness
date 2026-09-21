package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ScannerInventory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * Git-selected working files and the checked copy operation used by Qodana.
 */
@UtilityClass
final class QodanaSnapshot {

    /**
     * Copies metadata recursively and selected working files literally, without traversing their parents.
     * Each tar operation is a separate command so a producer failure cannot be hidden by extraction.
     * The POSIX script takes the source, destination and NUL-delimited manifest as arguments.
     */
    static final String SCRIPT = """
        set -eu
        source="$1"
        destination="$2"
        manifest="$3"
        archive="${destination}.tar"
        mkdir -p "$destination"
        tar -C "$source" -cf "$archive" .git
        tar -C "$source" -rf "$archive" --no-recursion --null -T "$manifest"
        tar -C "$destination" -xf "$archive"
        rm "$archive"
        test "$(git -C "$destination" rev-parse --show-toplevel)" = "$destination"
        """;

    static Path prepare(Path root, Path output) throws IOException {
        Path metadata = root.resolve(".git");
        if (!Files.isDirectory(metadata, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Qodana requires an in-tree .git directory; use a standalone clone: " + root);
        }
        String files = new ScannerInventory(root).files().stream()
            .map(root::relativize)
            .map(path -> path + "\0")
            .collect(Collectors.joining());
        Files.createDirectories(output);
        return Files.writeString(output.resolve("source-files"), files);
    }
}
