package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The Git directories a worktree made by {@code git worktree add} keeps outside its own tree.
 *
 * <p>A clone keeps its history in the {@code .git} directory of its working tree. A linked worktree keeps
 * only a {@code .git} file there, naming the worktree's own directory, which in turn names the common
 * directory that holds the objects and the refs every worktree of the clone shares. A container that mounts
 * the working tree alone finds a pointer to nowhere: Git inside it reports no repository, and a check that
 * reads history through it reads nothing at all. A container meant to read the history mounts these
 * directories too, each at the path the pointers name, which keeps a relative pointer valid as well.
 *
 * <p>The pointers are read from the files rather than asked of Git, because the paths a container needs
 * are the ones written there, not the ones Git would resolve them to on the host.
 *
 * @param own    the worktree's own Git directory, holding its HEAD and index
 * @param common the directory holding the objects and refs of the whole clone
 */
public record LinkedWorktree(Path own, Path common) {

    private static final String POINTER = "gitdir:";

    /**
     * The linked worktree whose working tree the root is, if it is one.
     *
     * @param root the working tree root
     * @return the worktree's Git directories, or nothing for a clone whose {@code .git} is a directory
     * @throws IllegalStateException when the {@code .git} file names no Git directory
     */
    public static Optional<LinkedWorktree> of(Path root) {
        Path metadata = root.resolve(".git");
        if (!Files.isRegularFile(metadata, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        String pointer = read(metadata).strip();
        if (!pointer.startsWith(POINTER)) {
            throw new IllegalStateException(
                metadata + " names no Git directory; a linked worktree's .git file reads gitdir: <path>"
            );
        }
        Path own = root.resolve(pointer.substring(POINTER.length()).strip()).normalize();
        Path commonPointer = own.resolve("commondir");
        Path common = Files.isRegularFile(commonPointer, LinkOption.NOFOLLOW_LINKS)
            ? own.resolve(read(commonPointer).strip()).normalize()
            : own;
        return Optional.of(new LinkedWorktree(own, common));
    }

    /**
     * The directories a container mounts besides the working tree, the worktree's own one only when it does
     * not already lie inside the common one, as it does unless it was moved there by hand.
     *
     * @return the common directory, then the worktree's own one if it lies elsewhere
     */
    public List<Path> directories() {
        return Stream.concat(
            Stream.of(this.common), Stream.of(this.own).filter(directory -> !directory.startsWith(this.common))
        ).toList();
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + file, exception);
        }
    }
}
