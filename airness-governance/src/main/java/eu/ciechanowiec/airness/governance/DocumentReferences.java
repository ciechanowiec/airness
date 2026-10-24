package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Reads the repository paths an instruction file names.
 *
 * <p>Only a high-signal subset is read, so that the rule never reports a reference that is fine: a
 * backticked token that holds a slash and ends in a file extension, which is a full path to a concrete
 * file. A bare file name is shorthand, a directory or glob may name what does not exist yet, a token with
 * a space is a command line, and a URL is not a repository path.
 */
@UtilityClass
final class DocumentReferences {

    private static final Pattern BACKTICKED = Pattern.compile("`([^`\\n]+)`");
    private static final Pattern TRAILING_GLOB = Pattern.compile("/\\*\\*?$");
    private static final Pattern EXTENSION = Pattern.compile(
        "\\.(?:tsx?|mts|cts|mjs|cjs|js|jsonc?|ya?ml|md|css|scss|grit|adoc|xml|sh|properties|toml|java)$"
    );
    private static final String SLASH = "/";

    /**
     * The repository paths a document names, in the order it names them.
     *
     * @param markdown the document
     * @return each path once, relative and without a leading slash
     */
    static List<String> paths(CharSequence markdown) {
        return BACKTICKED.matcher(markdown).results()
            .map(match -> match.group(1).strip())
            .filter(token -> token.contains(SLASH) && !token.contains(" ") && !token.contains("://"))
            .map(token -> TRAILING_GLOB.matcher(token).replaceFirst(""))
            .filter(token -> !token.contains("*") && EXTENSION.matcher(token).find())
            .map(token -> token.startsWith(SLASH) ? token.substring(1) : token)
            .distinct()
            .toList();
    }
}
