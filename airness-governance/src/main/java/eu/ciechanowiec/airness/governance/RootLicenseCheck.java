package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Rejects standalone license files beside the repository's root {@code pom.xml}.
 *
 * <p>The Maven model is the source of the project's license declaration. A second declaration beside
 * it can drift or imply a different license, so the root may contain none of {@code LICENSE},
 * {@code LICENSE.TXT}, or {@code LICENSE.MD}, under any casing. The check reads directory entries rather than Git's
 * committable set so an ignored or untracked file cannot make the repository non-compliant without
 * being seen.
 */
public final class RootLicenseCheck {

    private static final String HEADLINE
        = "License files named LICENSE, LICENSE.TXT, or LICENSE.MD must not sit beside the root pom.xml";
    private static final Set<String> FORBIDDEN = Set.of("license", "license.txt", "license.md");

    private final Path root;

    /**
     * Records the repository root whose direct entries will be checked.
     *
     * @param root the directory containing the root {@code pom.xml}
     */
    public RootLicenseCheck(Path root) {
        this.root = root;
    }

    /**
     * Whether a forbidden root license file is present.
     *
     * @return the one repository-file rule and every filename that breaks it
     */
    public List<Findings> findings() {
        return List.of(new Findings(HEADLINE, this.forbidden()));
    }

    private List<String> forbidden() {
        try (Stream<Path> entries = Files.list(this.root)) {
            return entries
                .filter(RootLicenseCheck::fileEntry)
                .filter(RootLicenseCheck::forbiddenName)
                .map(path -> path.getFileName().toString())
                .sorted()
                .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not inspect repository root " + this.root, exception);
        }
    }

    private static boolean fileEntry(Path path) {
        return Files.isSymbolicLink(path) || Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS);
    }

    private static boolean forbiddenName(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return FORBIDDEN.contains(name);
    }
}
