package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import lombok.experimental.UtilityClass;

/**
 * Computes a content fingerprint of every tracked or committable working-tree path.
 */
@UtilityClass
public final class TreeFingerprint {

    private static final byte SYMBOLIC_LINK = 1;
    private static final byte REGULAR_FILE = 2;
    private static final byte OTHER_ENTRY = 3;

    /**
     * Fingerprints path names, file contents, and symbolic-link targets in deterministic order.
     *
     * @param root repository root
     * @return hexadecimal SHA-256 fingerprint
     */
    public static String from(Path root) {
        MessageDigest digest = sha256();
        paths(root).forEach(path -> update(digest, root, path));
        return HexFormat.of().formatHex(digest.digest());
    }

    /**
     * The paths this fingerprints, each once, in an order that does not depend on the platform.
     *
     * <p>This asks git the same question {@link Repository#trackedFiles} asks, and keeps two answers
     * that method drops. An entry that is neither a file nor a symbolic link stays, because a build
     * that leaves one behind has still changed the tree, and that is the whole subject here.
     *
     * <p>Distinct, because an index holding an unmerged path lists it once per stage, and hashing one
     * file three times would move the fingerprint of a tree whose content never moved.
     *
     * @param root the working tree root
     * @return the committable paths, deduplicated and ordered
     */
    private static List<Path> paths(Path root) {
        String listing = GitPlumbing.run(
            root, List.of("ls-files", "-z", "--cached", "--others", "--exclude-standard")
        );
        return Arrays.stream(listing.split("\0", -1))
            .filter(name -> !name.isEmpty())
            .map(root::resolve)
            .distinct()
            .sorted(Comparator.comparing(path -> relative(root, path)))
            .toList();
    }

    /**
     * A path as the fingerprint spells it, which is with forward slashes whatever the platform uses.
     *
     * <p>The name goes into the digest as well as into the sort, so a platform separator would give one
     * tree two fingerprints and make the verdict depend on who ran the build.
     *
     * @param root the working tree root
     * @param path the path to render
     * @return the repository-relative name
     */
    private static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static void update(MessageDigest digest, Path root, Path path) {
        try {
            digest.update(relative(root, path).getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            if (Files.isSymbolicLink(path)) {
                digest.update(SYMBOLIC_LINK);
                digest.update(Files.readSymbolicLink(path).toString().getBytes(StandardCharsets.UTF_8));
            } else if (Files.isRegularFile(path)) {
                digest.update(REGULAR_FILE);
                digest.update(Files.readAllBytes(path));
            } else {
                digest.update(OTHER_ENTRY);
            }
            digest.update((byte) 0);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not fingerprint " + path, exception);
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
