package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Reads the repository paths an instruction file names.
 *
 * <p>Only a high-signal subset is read, so that the rule never reports a reference that is fine: a
 * backticked token that holds a slash and ends in a file extension, which is a full path to a concrete
 * file. A bare file name is shorthand, a directory or glob may name what does not exist yet, a token with
 * a space is a command line, and a URL is not a repository path.
 *
 * <p>The npm scripts a document names are read too, for the node layer: a backticked token of a known script
 * family, or a {@code pnpm run} invocation.
 */
@UtilityClass
final class InstructionReferences {

    private static final Pattern BACKTICKED = Pattern.compile("`([^`\\n]+)`");
    private static final Pattern TRAILING_GLOB = Pattern.compile("/\\*\\*?$");
    private static final Pattern EXTENSION = Pattern.compile(
        "\\.(?:tsx?|mts|cts|mjs|cjs|js|jsonc?|ya?ml|md|css|scss|grit|adoc|xml|sh|properties|toml|java)$"
    );
    private static final String SLASH = "/";
    private static final Pattern PNPM_RUN = Pattern.compile("pnpm run ([a-z][a-z0-9:_-]*)");
    /*
     * The families a backticked token has to belong to before it is read as an npm script. A family rather
     * than a list of names, so a renamed script is still checked rather than silently dropped.
     */
    private static final Pattern SCRIPT = Pattern.compile(
        "^(?:verify(?::full)?|format|knip|(?:lint|test|generate|ensure|with|seed):[a-z][a-z:-]*)$"
    );
    private static final String SCOPE = "@";

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

    /**
     * The npm scripts a document names: a backticked token of a script family, and every
     * {@code pnpm run <name>}.
     *
     * @param markdown the document
     * @return each script name once, in the order the document names them
     */
    static List<String> scripts(CharSequence markdown) {
        return Stream.concat(
            BACKTICKED.matcher(markdown).results()
                .map(match -> match.group(1).strip())
                .filter(token -> SCRIPT.matcher(token).matches()),
            PNPM_RUN.matcher(markdown).results().map(match -> match.group(1))
        ).distinct().toList();
    }

    /**
     * The package a path-shaped token would name if it were a module specifier: its scope and name, or its
     * first segment.
     *
     * @param token the token
     * @return the package name it starts with
     */
    static String specifierPackage(String token) {
        String[] segments = token.split(SLASH, 3);
        return token.startsWith(SCOPE) && segments.length > 1 ? segments[0] + SLASH + segments[1] : segments[0];
    }
}
