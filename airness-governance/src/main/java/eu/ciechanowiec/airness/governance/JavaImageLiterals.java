package eu.ciechanowiec.airness.governance;

import java.util.Comparator;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Reads the container images a Java source names through Testcontainers: the literal handed to
 * {@code DockerImageName.parse} and the literal handed to a container constructor.
 *
 * <p>Comments are blanked first, so a call a comment quotes is not judged, and a literal a text block
 * holds is left with it, since a text block is a document and not a reference. The constructor form is
 * read only in a source that imports Testcontainers, because a project's own {@code FooContainer("x")}
 * is not an image, and every literal has to read as a reference before it is judged, so a constructor
 * handed a sentence is not reported as an image nothing pins.
 *
 * <p>A reference built from a constant or by concatenation is not read. That is the documented limit of
 * a reader that has no compiler, and the module that pulls the image is judged by name beside it.
 */
@UtilityClass
final class JavaImageLiterals {

    private static final Pattern PARSE = Pattern.compile(
        "DockerImageName\\s*\\.\\s*parse\\s*\\(\\s*\"(?<value>[^\"]*)\""
    );
    private static final Pattern CONSTRUCTOR = Pattern.compile(
        "new\\s+\\w*Container\\s*(?:<[^>]*>)?\\s*\\(\\s*\"(?<value>[^\"]*)\""
    );
    private static final Pattern REFERENCE = Pattern.compile("^[a-z0-9][a-z0-9._/:-]*(?:@sha256:[0-9a-f]{64})?$");
    private static final String TESTCONTAINERS = "import org.testcontainers";
    private static final String VALUE = "value";

    /**
     * Every image reference the source names as a literal.
     *
     * @param source the Java source
     * @return the references with their lines, in source order
     */
    static List<Located<String>> in(CharSequence source) {
        String code = JavaCode.withoutComments(source);
        Stream<MatchResult> parsed = PARSE.matcher(code).results();
        Stream<MatchResult> constructed = code.contains(TESTCONTAINERS)
            ? CONSTRUCTOR.matcher(code).results()
            : Stream.empty();
        return Stream.concat(parsed, constructed)
            .filter(match -> REFERENCE.matcher(match.group(VALUE)).matches())
            .map(match -> new Located<>(JavaCode.lineOf(code, match.start()), match.group(VALUE)))
            .sorted(Comparator.comparingInt(Located::line))
            .toList();
    }
}
