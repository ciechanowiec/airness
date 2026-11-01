package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.LinkedWorktree;
import eu.ciechanowiec.airness.governance.ScannerInventory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
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
     * The POSIX script takes the source, destination and NUL-delimited manifest as arguments. For a linked
     * worktree it takes two more, the worktree's own Git directory and the common one, and gives the copy a
     * .git directory of its own: the common directory without the other worktrees, holding this worktree's
     * HEAD and index, which is a standalone repository at the commit and with the staged content of the
     * worktree.
     */
    static final String SCRIPT = """
        set -eu
        source="$1"
        destination="$2"
        manifest="$3"
        archive="${destination}.tar"
        mkdir -p "$destination"
        if [ "$#" -eq 5 ]; then
            mkdir "$destination/.git"
            tar -C "$5" -cf "$archive" --exclude=./worktrees .
            tar -C "$destination/.git" -xf "$archive"
            rm -f "$destination/.git/index"
            cp "$4/HEAD" "$destination/.git/HEAD"
            if [ -f "$4/index" ]; then cp "$4/index" "$destination/.git/index"; fi
            tar -C "$source" -cf "$archive" --no-recursion --null -T "$manifest"
        else
            tar -C "$source" -cf "$archive" .git
            tar -C "$source" -rf "$archive" --no-recursion --null -T "$manifest"
        fi
        tar -C "$destination" -xf "$archive"
        rm "$archive"
        test "$(git -C "$destination" rev-parse --show-toplevel)" = "$destination"
        """;

    /**
     * Where the container mounts a linked worktree's own Git directory.
     */
    static final String OWN = "/opt/git-own";

    /**
     * Where the container mounts a linked worktree's common Git directory.
     */
    static final String COMMON = "/opt/git-common";

    static Path prepare(Path root, Path output) throws IOException {
        Path metadata = root.resolve(".git");
        if (!Files.isDirectory(metadata, LinkOption.NOFOLLOW_LINKS) && LinkedWorktree.of(root).isEmpty()) {
            throw new IOException(
                "Qodana reads the Git metadata of the repository, but " + root + " has neither a .git directory"
                    + " nor the .git file of a linked worktree"
            );
        }
        String files = new ScannerInventory(root).files().stream()
            .map(root::relativize)
            .map(path -> path + "\0")
            .collect(Collectors.joining());
        Files.createDirectories(output);
        return Files.writeString(output.resolve("source-files"), files);
    }

    /**
     * The docker arguments that mount a linked worktree's Git directories for the script, none for a clone.
     *
     * @param root the working tree root
     * @return the {@code -v} arguments, read-only
     * @throws IOException when a directory the worktree's pointers name does not exist on the host
     */
    static List<String> volumes(Path root) throws IOException {
        List<Path> linked = linked(root);
        return linked.isEmpty()
            ? List.of()
            : List.of("-v", linked.getFirst() + ":" + OWN + ":ro", "-v", linked.getLast() + ":" + COMMON + ":ro");
    }

    /**
     * The host directories {@link #volumes} mounts, for the check to read before it starts.
     *
     * @param root the working tree root
     * @return the worktree's own and common Git directories, none for a clone
     * @throws IOException when one of them does not exist on the host
     */
    static List<Path> linked(Path root) throws IOException {
        Optional<LinkedWorktree> worktree = LinkedWorktree.of(root);
        if (worktree.isEmpty()) {
            return List.of();
        }
        return List.of(worktree.orElseThrow().own().toRealPath(), worktree.orElseThrow().common().toRealPath());
    }
}
