package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Reports Spring AI operations whose model-facing contract says nothing about what they do.
 *
 * <p>The four annotations this reads publish methods to a model. Their descriptions are the only text
 * that tells the model when the operation applies, so an absent or blank one makes selection depend on
 * a Java name written for a human compiler rather than on a model-facing contract.
 *
 * <p>Only the exact Spring AI imports and fully qualified annotations count. {@code Tool} is a generic
 * enough name for another library to own, and a governance rule cannot infer its supplier from the
 * spelling alone. Values are resolved when they are literals or constants in the same source. An
 * expression this source cannot settle is passed over rather than guessed at.
 */
@UtilityClass
final class SpringAiRules {

    private static final List<AiAnnotation> ANNOTATIONS = List.of(
        new AiAnnotation("Tool", "org.springframework.ai.tool.annotation.Tool"),
        new AiAnnotation("McpTool", "org.springframework.ai.mcp.annotation.McpTool"),
        new AiAnnotation("McpPrompt", "org.springframework.ai.mcp.annotation.McpPrompt"),
        new AiAnnotation("McpResource", "org.springframework.ai.mcp.annotation.McpResource")
    );
    private static final Pattern STRING_CONSTANT = Pattern.compile(
        "\\bstatic\\s+final\\s+String\\s+(\\w+)\\s*=\\s*\"([^\"]*)\""
    );
    private static final Pattern DESCRIPTION = Pattern.compile(
        "\\bdescription\\s*=\\s*(\"[^\"]*\"|[A-Z][A-Z0-9_]*)\\s*[,)]"
    );
    private static final Pattern DESCRIPTION_MEMBER = Pattern.compile("\\bdescription\\s*=");

    /**
     * Every Spring AI annotation carrying no description the source can read as nonblank.
     *
     * @param source the Java source to read
     * @return one offence per missing or blank description
     */
    static List<String> missingDescriptions(CharSequence source) {
        String readable = JavaCode.withoutComments(source);
        String code = JavaCode.blanked(source);
        Map<String, String> constants = STRING_CONSTANT.matcher(readable).results()
            .collect(Collectors.toMap(found -> found.group(1), found -> found.group(2), (first, _) -> first));
        return ANNOTATIONS.stream()
            .filter(annotation -> annotation.available(readable))
            .flatMap(annotation -> annotation.markers(code).stream())
            .flatMap(marker -> offence(source, readable, constants, marker).stream())
            .toList();
    }

    private static Optional<String> offence(
        CharSequence source, String readable, Map<String, String> constants, MatchResult marker
    ) {
        String annotation = declaration(readable, marker);
        Optional<MatchResult> description = DESCRIPTION.matcher(annotation).results().findFirst();
        boolean missing = !DESCRIPTION_MEMBER.matcher(annotation).find();
        boolean blank = description
            .flatMap(found -> value(constants, found.group(1)))
            .filter(String::isBlank)
            .isPresent();
        return missing || blank
            ? Optional.of(
                "line " + JavaCode.lineOf(source, marker.start())
                    + ": a Spring AI tool, prompt or resource has no nonblank description, so the model"
                    + " has no explicit contract for selecting it"
            )
            : Optional.empty();
    }

    private static String declaration(String readable, MatchResult marker) {
        int opening = readable.indexOf('(', marker.end());
        if (opening < 0 || !readable.substring(marker.end(), opening).isBlank()) {
            return readable.substring(marker.start(), marker.end());
        }
        int closing = SpringMembers.closing(JavaCode.blanked(readable), opening);
        return readable.substring(marker.start(), Math.min(closing + 1, readable.length()));
    }

    private static Optional<String> value(Map<String, String> constants, String written) {
        return written.startsWith("\"")
            ? Optional.of(written.substring(1, written.length() - 1))
            : Optional.ofNullable(constants.get(written));
    }

    /**
     * One model-facing Spring AI annotation.
     *
     * @param simple    source-level name
     * @param qualified fully qualified name
     */
    private record AiAnnotation(String simple, String qualified) {

        private boolean available(String source) {
            String direct = "import " + this.qualified + ';';
            String wildcard = "import " + this.qualified.substring(0, this.qualified.lastIndexOf('.')) + ".*;";
            return source.contains(direct) || source.contains(wildcard) || source.contains('@' + this.qualified);
        }

        private List<MatchResult> markers(String source) {
            Pattern qualifiedMarker = Pattern.compile("@" + Pattern.quote(this.qualified) + "\\b");
            Pattern simpleMarker = Pattern.compile("@" + Pattern.quote(this.simple) + "\\b");
            return Stream.concat(
                qualifiedMarker.matcher(source).results(),
                simpleMarker.matcher(source).results()
            ).toList();
        }
    }
}
