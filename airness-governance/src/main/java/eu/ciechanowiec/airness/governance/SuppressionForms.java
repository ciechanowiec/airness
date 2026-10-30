package eu.ciechanowiec.airness.governance;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import lombok.SneakyThrows;

/**
 * The further suppression forms the stack layers present declare for the node suppression ceiling.
 *
 * <p>A layer declares its forms in {@code airness/suppressions/<capability>.txt} on the plugin classpath, one
 * regular expression per line, as it declares its generated files in {@code airness/generated/}. The generated
 * files it declares there leave the ceiling's line count, and these forms join the directives it counts. A layer adds
 * both through its own jar, which is how a stack layer contributes its generated types and its own suppression
 * markers without a change here.
 *
 * @param classes the classloader carrying the layer declarations
 */
public record SuppressionForms(ClassLoader classes) {

    private static final String DECLARATIONS = "airness/suppressions/";
    private static final String TEXT = ".txt";
    private static final String COMMENT = "#";

    /**
     * Every form the given capabilities declare.
     *
     * @param capabilities the capabilities present in the repository
     * @return the forms, in capability order and then in declaration order
     */
    public List<Pattern> forms(Collection<Capability> capabilities) {
        return capabilities.stream().distinct().sorted()
            .flatMap(capability -> this.read(DECLARATIONS + capability.id() + TEXT).stream())
            .flatMap(String::lines)
            .map(String::strip)
            .filter(line -> !line.isEmpty() && !line.startsWith(COMMENT))
            .map(Pattern::compile)
            .toList();
    }

    @SneakyThrows
    private Optional<String> read(String resource) {
        try (InputStream stream = this.classes.getResourceAsStream(resource)) {
            return Optional.ofNullable(stream).map(SuppressionForms::drain);
        }
    }

    @SneakyThrows
    private static String drain(InputStream stream) {
        return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
}
