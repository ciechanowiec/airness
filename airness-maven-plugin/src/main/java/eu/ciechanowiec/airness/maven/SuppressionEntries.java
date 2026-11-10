package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.SuppressionDocument;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * The documents {@code airness.dependency-check.suppression.file} names, each one a local file inside the
 * repository.
 *
 * <p>Dependency-Check downloads an entry that names a URL or a {@code file:} URI, and reads a path wherever it
 * points, so an entry this check passed over would reach the scan unread: a document on a host, or outside the
 * tree, could excuse every advisory with no notes, no plan and no date. Every entry is therefore refused unless it
 * names an existing regular file inside the repository root, and that file is then held to the suppression rules.
 * The one exception is the default path while nothing is kept there, which names no document until one is: the
 * parent reads the setting only once a document exists at that path.
 */
@UtilityClass
final class SuppressionEntries {

    /**
     * Where a module keeps its suppression document, relative to the module, which the default value names.
     */
    static final String DEFAULT = ".airness/dependency-check-suppressions.xml";
    private static final String PROPERTY = "airness.dependency-check.suppression.file";
    // A URI scheme: two characters at least, so a Windows drive letter still reads as a path.
    private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]+:");

    /**
     * Every problem of every declared entry: an entry that is not a local document inside the repository, or the
     * problems of the document an entry names.
     *
     * @param declared the comma-separated value of the setting, if any
     * @param basedir  the module directory, against which a relative entry is resolved
     * @param root     the repository root
     * @return the problems, entry by entry
     */
    static List<String> problems(@Nullable String declared, Path basedir, Path root) {
        return CommaList.entries(declared).stream()
            .flatMap(entry -> entryProblems(entry, basedir, root))
            .toList();
    }

    private static Stream<String> entryProblems(String entry, Path basedir, Path root) {
        if (SCHEME.matcher(entry).find()) {
            return Stream.of(refusal(entry, "is a URL, which the scan would download"));
        }
        Path document = basedir.resolve(entry).normalize();
        boolean unkept = document.equals(basedir.resolve(DEFAULT).normalize())
            && Files.notExists(document, LinkOption.NOFOLLOW_LINKS);
        return unkept ? Stream.empty() : documentProblems(entry, document, root);
    }

    private static Stream<String> documentProblems(String entry, Path document, Path root) {
        Optional<String> refused = Files.isRegularFile(document)
            ? Optional.of(refusal(entry, "lies outside the repository"))
                .filter(_ -> !real(document).startsWith(real(root)))
            : Optional.of(refusal(entry, "is not an existing regular file"));
        return refused.map(Stream::of).orElseGet(() -> new SuppressionDocument(document).problems().stream());
    }

    private static String refusal(String entry, String reason) {
        return PROPERTY + " names " + entry + ", which " + reason
            + "; name a suppression document kept inside the repository, such as " + DEFAULT
            + " in the module whose dependency it excuses";
    }

    private static Path real(Path path) {
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not resolve " + path, exception);
        }
    }
}
