package eu.ciechanowiec.airness.governance;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Locates every rule a JavaScript, TypeScript or stylesheet source turns off, one entry per rule.
 *
 * <p>These are the directives the node gates honour: ESLint's, Oxlint's and Stylelint's disable comments,
 * Biome's ignore comments, and TypeScript's {@code @ts-ignore}, {@code @ts-expect-error} and
 * {@code @ts-nocheck}. A disable comment naming three rules sets three aside, as an annotation naming three
 * does in Java, and one naming no rule sets every rule aside and counts once. The text after {@code --} is the
 * reason, which is prose about the suppression rather than a rule. A directive is read only at the start of a
 * comment, where the tools read it. The coverage directives of V8, c8 and Istanbul count too, because they
 * delete the measurement the coverage floor reads, and so do the secret scan's {@code gitleaks:allow} and the
 * layout sweep's exemption attribute, each of which turns a rule off where it stands.
 */
@UtilityClass
public final class NodeSuppressions {

    /**
     * The file name extensions whose sources carry these directives.
     */
    public static final Set<String> EXTENSIONS = Set.of(
        "ts", "tsx", "mts", "cts", "js", "jsx", "mjs", "cjs", "css", "scss"
    );

    private static final Pattern DISABLE = Pattern.compile(
        "(?://|/\\*)\\s*((?:eslint|oxlint|stylelint)-disable(?:-next-line|-line)?)(?=[\\s*]|$)([^\\n]*)"
    );
    private static final Pattern BIOME = Pattern.compile("(?://|/\\*)\\s*(biome-ignore(?:-all|-start)?)\\s+([^\\s:]+)");
    private static final Pattern TYPESCRIPT = Pattern.compile(
        "(?://|/\\*)\\s*(@ts-(?:ignore|expect-error|nocheck))\\b"
    );
    // The coverage directives delete the measurement the per-file coverage floor reads, which no other
    // suppression does, so each counts like a disabled rule. The closing "stop" of a range was counted at its
    // start. The secret scan's line marker and the layout sweep's exemption attribute turn a rule off too.
    private static final Pattern COVERAGE = Pattern.compile(
        "(?://|/\\*)\\s*((?:v8|c8|istanbul) ignore)(?!\\s+stop)(?:\\s+([\\w-]+))?"
    );
    private static final Pattern BARE = Pattern.compile("gitleaks:allow|data-airness-layout(?:=|['\"]\\s*:)");
    private static final String REASON = "--";
    private static final String COMMENT_END = "*/";

    /**
     * Whether a file is a source these directives can appear in.
     *
     * @param name the file name
     * @return whether its extension is one of {@link #EXTENSIONS}
     */
    public static boolean readable(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 && EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /**
     * Every rule the source suppresses, in source order within each kind of directive.
     *
     * @param source the decoded text of a source
     * @return one entry per suppressed rule, naming its line and the rule
     */
    public static List<String> in(String source) {
        return in(source, List.of());
    }

    /**
     * Every rule the source suppresses, with the further forms a stack layer declares, each counting once.
     *
     * @param source the decoded text of a source
     * @param forms  the further suppression forms, each a regular expression
     * @return one entry per suppressed rule, naming its line and the rule
     */
    public static List<String> in(String source, Collection<Pattern> forms) {
        return Stream.of(
            DISABLE.matcher(source).results().flatMap(match -> disabled(source, match)),
            BIOME.matcher(source).results().map(match -> entry(source, match, match.group(1) + ' ' + match.group(2))),
            TYPESCRIPT.matcher(source).results().map(match -> entry(source, match, match.group(1))),
            COVERAGE.matcher(source).results().map(match -> entry(source, match, match.group(1))),
            BARE.matcher(source).results().map(match -> entry(source, match, match.group())),
            forms.stream().flatMap(
                form -> form.matcher(source).results().map(match -> entry(source, match, match.group()))
            )
        ).flatMap(stream -> stream).toList();
    }

    private static Stream<String> disabled(String source, MatchResult match) {
        String rest = match.group(2);
        int end = rest.indexOf(COMMENT_END);
        String body = end >= 0 ? rest.substring(0, end) : rest;
        int reason = body.indexOf(REASON);
        List<String> rules = Stream.of((reason >= 0 ? body.substring(0, reason) : body).split(",", -1))
            .map(String::strip).filter(rule -> !rule.isEmpty()).toList();
        String directive = match.group(1);
        return rules.isEmpty() ? Stream.of(entry(source, match, directive + " (every rule)"))
            : rules.stream().map(rule -> entry(source, match, directive + ' ' + rule));
    }

    private static String entry(String source, MatchResult match, String rule) {
        return "line %d: %s".formatted(JavaCode.lineOf(source, match.start()), rule);
    }
}
