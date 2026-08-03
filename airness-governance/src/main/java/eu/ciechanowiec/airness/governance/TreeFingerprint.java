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

    private static List<Path> paths(Path root) {
        String listing = GitPlumbing.run(
            root, List.of("ls-files", "-z", "--cached", "--others", "--exclude-standard")
        );
        return Arrays.stream(listing.split("\0"))
            .filter(name -> !name.isEmpty())
            .map(root::resolve)
            .sorted(Comparator.comparing(path -> root.relativize(path).toString()))
            .toList();
    }

    private static void update(MessageDigest digest, Path root, Path path) {
        try {
            digest.update(root.relativize(path).toString().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            if (Files.isSymbolicLink(path)) {
                digest.update(Files.readSymbolicLink(path).toString().getBytes(StandardCharsets.UTF_8));
            } else if (Files.isRegularFile(path)) {
                digest.update(Files.readAllBytes(path));
            } else {
                digest.update((byte) 1);
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
