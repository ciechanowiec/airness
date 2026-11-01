package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A linked worktree is read from the pointers git worktree add writes, so a container can mount the
 * directories the history lives in at the paths those pointers name.
 */
class LinkedWorktreeTest {

    @Test
    void findsNothingBesidesTheTreeOfAClone() {
        Path root = new GitFixture("linked-clone").write("file.txt", "content\n").commit("test: one").root();
        assertEquals(Optional.empty(), LinkedWorktree.of(root), "a clone keeps its history inside its tree");
    }

    @Test
    void findsTheWorktreesOwnAndTheSharedDirectoriesGitWrote() {
        GitFixture clone = new GitFixture("linked-main").write("file.txt", "content\n").commit("test: one");
        Path worktree = clone.root().resolveSibling(clone.root().getFileName() + "-second");
        clone.git("worktree", "add", "--quiet", "--detach", worktree.toString());
        LinkedWorktree linked = LinkedWorktree.of(worktree).orElseThrow();
        Path common = clone.root().resolve(".git");
        assertEquals(common, linked.common(), "the objects and refs are the main clone's");
        assertTrue(linked.own().startsWith(common.resolve("worktrees")), "its HEAD and index sit in the clone");
        assertEquals(List.of(common), linked.directories(), "one mount reaches both when one holds the other");
    }

    @Test
    @SneakyThrows
    void keepsARelativePointerRelativeToTheTreeItIsWrittenIn(@TempDir Path directory) {
        Path own = Files.createDirectories(directory.resolve("main/.git/worktrees/second"));
        Files.writeString(own.resolve("commondir"), "../..\n");
        Path worktree = Files.createDirectories(directory.resolve("second"));
        Files.writeString(worktree.resolve(".git"), "gitdir: ../main/.git/worktrees/second\n");
        LinkedWorktree linked = LinkedWorktree.of(worktree).orElseThrow();
        assertEquals(own, linked.own(), "the pointer is read against the worktree");
        assertEquals(directory.resolve("main/.git"), linked.common(), "commondir is read against the own one");
    }

    @Test
    @SneakyThrows
    void mountsAnOwnDirectoryThatLiesOutsideTheSharedOne(@TempDir Path directory) {
        Path common = Files.createDirectories(directory.resolve("main/.git"));
        Path own = Files.createDirectories(directory.resolve("elsewhere/second"));
        Files.writeString(own.resolve("commondir"), common + "\n");
        Path worktree = Files.createDirectories(directory.resolve("second"));
        Files.writeString(worktree.resolve(".git"), "gitdir: " + own + "\n");
        assertEquals(
            List.of(common, own), LinkedWorktree.of(worktree).orElseThrow().directories(),
            "a worktree directory moved out of the clone is a mount of its own"
        );
    }

    @Test
    @SneakyThrows
    void takesAWorktreeWithoutACommonPointerAsItsOwnHistory(@TempDir Path directory) {
        Path own = Files.createDirectories(directory.resolve("metadata"));
        Path worktree = Files.createDirectories(directory.resolve("tree"));
        Files.writeString(worktree.resolve(".git"), "gitdir: " + own + "\n");
        assertEquals(new LinkedWorktree(own, own), LinkedWorktree.of(worktree).orElseThrow());
    }

    @Test
    @SneakyThrows
    void namesAGitFileThatPointsNowhere(@TempDir Path directory) {
        Files.writeString(directory.resolve(".git"), "not a pointer\n");
        IllegalStateException thrown = assertThrows(
            IllegalStateException.class, () -> LinkedWorktree.of(directory)
        );
        assertTrue(thrown.toString().contains("gitdir:"), "the reader is told what the file should hold");
    }
}
