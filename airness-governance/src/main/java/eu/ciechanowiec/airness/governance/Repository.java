package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import lombok.experimental.UtilityClass;

/**
 * Locates the repository working tree and reads the content a commit from it would carry. A path
 * deleted in an uncommitted change is absent from the working tree and therefore absent from a scan.
 * Binary files and files that are not valid UTF-8 are reported as absent text, so the callers scan only
 * decodable source and prose.
 */
@UtilityClass
public final class Repository {

    /**
     * The working tree root, asked of git rather than assumed from the current directory.
     *
     * <p>The current directory is the wrong answer under a multi-module build, where it is the module
     * being built rather than the repository. A scan rooted there would run {@code git ls-files} inside
     * one module and report green over a subtree, having never read the sibling modules, the root
     * documents, or the workflow files. Asking git makes the answer the same from every module and from
     * a test.
     *
     * @param start a directory inside the working tree
     * @return the working tree root
     * @throws IllegalStateException when {@code start} is not inside a git working tree
     */
    public static Path rootFrom(Path start) {
        String toplevel = GitPlumbing.run(start, List.of("rev-parse", "--show-toplevel")).strip();
        return Path.of(toplevel).toAbsolutePath().normalize();
    }

    /**
     * Whether the clone carries a truncated history.
     *
     * <p>Every check that reads history reports green over a shallow clone, having found nothing to
     * object to in the handful of commits it was given. The caller fails on this rather than scanning,
     * because a partial scan and a clean one are indistinguishable in the log.
     *
     * @param root the working tree root
     * @return whether the history is truncated
     */
    public static boolean isShallow(Path root) {
        return "true".equals(GitPlumbing.run(root, List.of("rev-parse", "--is-shallow-repository")).strip());
    }

    /**
     * Whether the working tree has any history at all.
     *
     * <p>A repository whose first commit is not yet written is a legitimate state, and the checks that
     * read history have nothing to say about it. Telling that apart from a truncated clone matters,
     * because the two look alike from inside a check and only one of them is a problem: an unborn HEAD
     * is a repository with no commits, while a shallow clone is a repository whose commits were not
     * fetched.
     *
     * <p>The question is asked of HEAD rather than of every ref, because HEAD is what the checks that act
     * on the answer go on to read. Counting every ref answers a question nobody asked: a fresh orphan
     * branch beside an existing one is an unborn HEAD in a repository that has commits, and a guard
     * counting those commits would wave the readers through to a HEAD that resolves to nothing.
     *
     * @param root the working tree root
     * @return whether HEAD names a commit
     */
    public static boolean hasCommits(Path root) {
        return GitPlumbing.attempt(root, List.of("rev-parse", "--verify", "HEAD")).isPresent();
    }

    /**
     * Every file a commit from this working tree would carry: what git already tracks, plus what is
     * present and not ignored.
     *
     * <p>A file only git tracks is the wrong set to scan. New work is untracked until it is staged, so
     * a scan of the tracked set alone would pass over a whole feature's worth of new sources and report
     * green on a tree it had not read, then fail on the next run once those files were committed. The
     * check is meant to answer whether this tree is fit to commit, so it reads the tree that would be
     * committed. Ignored files stay out, being build output rather than content.
     *
     * @param root the working tree root
     * @return the committable files present in the working tree
     */
    static List<Path> trackedFiles(Path root) {
        String listing = GitPlumbing.run(
            root, List.of("ls-files", "-z", "--cached", "--others", "--exclude-standard")
        );
        return Arrays.stream(listing.split("\0", -1))
            .filter(name -> !name.isEmpty())
            .map(root::resolve)
            .filter(Repository::committableEntry)
            .distinct()
            .toList();
    }

    /**
     * The readable text of a file, if it has any.
     *
     * <p>A path that is not there reads as no text rather than as a failure, which is what lets a caller
     * treat absence as its own verdict. A check that asserts a file is present reports the absence, and a
     * check that has nothing to do without the file says so in its own words. Neither would be able to if
     * reading threw, because the exception a missing file raises says only that a read failed.
     *
     * @param file the path to read
     * @return the decoded text, or nothing when the path is absent, binary, or not valid UTF-8
     */
    static Optional<String> readText(Path file) {
        if (Files.isSymbolicLink(file)) {
            return Optional.of(readLink(file));
        }
        return Optional.of(file)
            .filter(candidate -> Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS))
            .flatMap(Repository::readDecodable);
    }

    private static boolean committableEntry(Path path) {
        return Files.isSymbolicLink(path)
            || Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static String readLink(Path link) {
        try {
            return Files.readSymbolicLink(link).toString();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read symbolic link " + link, exception);
        }
    }

    private static Optional<String> readDecodable(Path file) {
        byte[] bytes = readBytes(file);
        return isBinary(bytes) ? Optional.empty() : decode(bytes);
    }

    private static byte[] readBytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + file, exception);
        }
    }

    private static boolean isBinary(byte[] bytes) {
        for (byte value : bytes) {
            if (value == 0) {
                return true;
            }
        }
        return false;
    }

    private static Optional<String> decode(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return Optional.of(decoder.decode(ByteBuffer.wrap(bytes)).toString());
        } catch (CharacterCodingException _) {
            return Optional.empty();
        }
    }
}
