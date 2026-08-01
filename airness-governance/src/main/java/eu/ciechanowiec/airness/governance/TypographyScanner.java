package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * Applies {@link TypographyRules} to every tracked text file, skipping the paths the caller declares
 * exempt and counting what each exemption cost. Binary files are skipped by content, having no prose to
 * read.
 *
 * <p>An exemption is a role rather than a convenience: a linter's own style library by nature contains
 * the glyphs it detects, and a vendored theme carries somebody else's typography. What the caller names
 * is therefore its own to justify, which is why the prefixes arrive as an argument and the counts go
 * back with the verdict instead of reaching a log this class does not own.
 */
@UtilityClass
final class TypographyScanner {

    /**
     * Reads every committable file under {@code root} that no prefix exempts.
     *
     * @param root             the working tree root
     * @param excludedPrefixes repository-relative path prefixes to leave unread
     * @return the violations found, and how many files each prefix kept out
     */
    static TypographyScan scan(Path root, Collection<String> excludedPrefixes) {
        List<Path> tracked = Repository.trackedFiles(root);
        List<String> violations = tracked.stream()
            .filter(file -> isIncluded(root, file, excludedPrefixes))
            .flatMap(file -> violationsFor(root, file).stream())
            .toList();
        return new TypographyScan(violations, skipped(root, tracked, excludedPrefixes));
    }

    private static boolean isIncluded(Path root, Path file, Collection<String> excludedPrefixes) {
        String relative = relative(root, file);
        return excludedPrefixes.stream().noneMatch(relative::startsWith);
    }

    private static Map<String, Long> skipped(Path root, Collection<Path> tracked, Collection<String> prefixes) {
        return prefixes.stream().collect(
            Collectors.toUnmodifiableMap(Function.identity(), prefix -> excludedBy(root, tracked, prefix))
        );
    }

    private static long excludedBy(Path root, Collection<Path> tracked, String prefix) {
        return tracked.stream().filter(file -> relative(root, file).startsWith(prefix)).count();
    }

    private static List<String> violationsFor(Path root, Path file) {
        return Repository.readText(file)
            .map(content -> render(relative(root, file), content))
            .orElseGet(List::of);
    }

    private static List<String> render(String relative, String content) {
        return TypographyRules.findViolations(content).stream()
            .map(violation -> format(relative, violation))
            .toList();
    }

    private static String format(String relative, TypographyViolation violation) {
        return "%s:%d:%d banned code point U+%04X".formatted(
            relative, violation.lineNumber(), violation.column(), violation.codePoint()
        );
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString();
    }
}
