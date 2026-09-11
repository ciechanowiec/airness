package eu.ciechanowiec.airness.governance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * A source hint may resolve a real repository import, but cannot substitute an empty or unrelated file.
 */
@UtilityClass
final class ShellSourceHints {

    private static final Pattern HINT = Pattern.compile("(?m)^\\s*#\\s*shellcheck\\s+[^\\r\\n]*?source=(\\S+)");

    static List<String> problems(Path root, Path script) {
        String text = Repository.readText(script).orElse("");
        return HINT.matcher(text).results().filter(match -> !matches(root, text, match))
            .map(_ -> root.relativize(script) + ": ShellCheck source hints must name the actual repository import")
            .toList();
    }

    private static boolean matches(Path root, String text, MatchResult hint) {
        Path path = root.resolve(Quotes.stripped(hint.group(1))).normalize();
        String next = text.substring(hint.end()).lines().map(String::strip)
            .filter(line -> !line.isEmpty() && !line.startsWith("#")).findFirst().orElse("");
        List<Path> references = ShellSources.references(root, next);
        return path.startsWith(root) && Files.isRegularFile(path) && references.contains(path);
    }
}
