package eu.ciechanowiec.airness.governance;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;

/**
 * Reads the container images a Java source names through Testcontainers: what is handed to
 * {@code DockerImageName.parse} and what is handed to a container constructor, whether the call writes
 * the reference out or names a constant the same source declares.
 *
 * <p>Comments are blanked first, so a call a comment quotes is not judged, and a literal a text block
 * holds is left with it, since a text block is a document and not a reference. The constructor form is
 * read only in a source that imports Testcontainers, because a project's own {@code FooContainer("x")}
 * is not an image, and every value has to read as a reference before it is judged, so a constructor
 * handed a sentence is not reported as an image nothing pins.
 *
 * <p>A constant of the same source is followed, because lifting the name of an image out of the call
 * that pulls it is the ordinary way to write one, and a reader that stopped at the literal would be
 * blind to the shape most sources take while reporting the same count of files read. A reference built
 * by concatenation, or from a constant another source declares, is still not read. That is the
 * documented limit of a reader that has no compiler, and the module that pulls the image is judged by
 * name beside it.
 */
@UtilityClass
final class JavaImageLiterals {

    private static final Pattern PARSE = Pattern.compile(
        "DockerImageName\\s*\\.\\s*parse\\s*\\(\\s*(?:\"(?<value>[^\"]*)\"|(?<name>\\w+)\\s*\\))"
    );
    private static final Pattern CONSTRUCTOR = Pattern.compile(
        "new\\s+\\w*Container\\s*(?:<[^>]*>)?\\s*\\(\\s*(?:\"(?<value>[^\"]*)\"|(?<name>\\w+)\\s*\\))"
    );
    private static final Pattern CONSTANT = Pattern.compile(
        "static\\s+final\\s+String\\s+(?<name>\\w+)\\s*=\\s*\"(?<value>[^\"]*)\"\\s*;"
    );
    private static final Pattern REFERENCE = Pattern.compile("^[a-z0-9][a-z0-9._/:-]*(?:@sha256:[0-9a-f]{64})?$");
    private static final String TESTCONTAINERS = "import org.testcontainers";
    private static final String VALUE = "value";
    private static final String NAME = "name";

    /**
     * Every image reference the source names, whether it writes one out or holds one in a constant.
     *
     * @param source the Java source
     * @return the references with the lines of the calls that pull them, in source order
     */
    static List<Located<String>> in(CharSequence source) {
        String code = JavaCode.withoutComments(source);
        Map<String, String> declared = constants(code);
        Stream<MatchResult> parsed = PARSE.matcher(code).results();
        Stream<MatchResult> constructed = code.contains(TESTCONTAINERS)
            ? CONSTRUCTOR.matcher(code).results()
            : Stream.empty();
        return Stream.concat(parsed, constructed)
            .map(match -> new Located<>(JavaCode.lineOf(code, match.start()), reference(match, declared)))
            .filter(located -> REFERENCE.matcher(located.value()).matches())
            .sorted(Comparator.comparingInt(Located::line))
            .toList();
    }

    // What the call was handed: the reference it wrote out, or the value of a constant this source
    // declares under the name it was handed, or nothing at all, which the caller then reads as a value
    // that is no reference and drops.
    private static String reference(MatchResult match, Map<String, String> declared) {
        return Optional.ofNullable(match.group(VALUE))
            .or(() -> Optional.ofNullable(match.group(NAME)).map(declared::get))
            .orElse("");
    }

    // Every string constant of the source, under the name it is declared with. The initialiser has to
    // be one literal and the whole of what is assigned, so a name built by concatenation is left unread
    // rather than read as the first part of itself.
    private static Map<String, String> constants(String code) {
        return CONSTANT.matcher(code)
            .results()
            .collect(
                Collectors.toMap(
                    match -> match.group(NAME),
                    match -> match.group(VALUE),
                    (first, _) -> first,
                    LinkedHashMap::new
                )
            );
    }
}
