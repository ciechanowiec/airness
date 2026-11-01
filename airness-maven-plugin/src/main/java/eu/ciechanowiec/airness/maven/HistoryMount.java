package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.LinkedWorktree;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * How a container mounts a repository so that Git inside it reads the whole history.
 *
 * <p>A clone is mounted at {@code /repo}, its {@code .git} directory with it. A worktree made by
 * {@code git worktree add} is mounted at its own path, beside the directories of the main clone that its
 * {@code .git} file leads to, each at its own path too, so that the pointers resolve inside the container as
 * they do on the host. Mounted at {@code /repo} alone, such a worktree hands Git a pointer to nowhere, and a
 * scanner reading history through it scans no commit and reports a clean history.
 *
 * @param volumes the {@code -v} arguments, every one read-only
 * @param path    where the working tree is inside the container
 * @param linked  the main clone's directories mounted beside it, as host paths, none for a clone
 */
record HistoryMount(List<String> volumes, String path, List<Path> linked) {

    private static final String REPOSITORY = "/repo";
    private static final String VOLUME = "-v";
    private static final String READ_ONLY = ":ro";

    HistoryMount {
        volumes = List.copyOf(volumes);
        linked = List.copyOf(linked);
    }

    /**
     * The mount for a repository root.
     *
     * @param root the working tree root
     * @return how a container reaches it and its history
     * @throws IOException when a directory the worktree's pointers name does not exist on the host
     */
    static HistoryMount of(Path root) throws IOException {
        Optional<LinkedWorktree> worktree = LinkedWorktree.of(root);
        if (worktree.isEmpty()) {
            return new HistoryMount(List.of(VOLUME, root + ":" + REPOSITORY + READ_ONLY), REPOSITORY, List.of());
        }
        List<String> volumes = new ArrayList<>(List.of(VOLUME, root.toRealPath() + ":" + root + READ_ONLY));
        List<Path> linked = new ArrayList<>();
        for (Path directory : worktree.orElseThrow().directories()) {
            Path source = directory.toRealPath();
            volumes.addAll(List.of(VOLUME, source + ":" + directory + READ_ONLY));
            linked.add(source);
        }
        return new HistoryMount(volumes, root.toString(), linked);
    }

    /**
     * A file of the working tree as the container sees it.
     *
     * @param relative the file's path inside the working tree
     * @return its path inside the container
     */
    String inside(String relative) {
        return this.path + "/" + relative;
    }
}
