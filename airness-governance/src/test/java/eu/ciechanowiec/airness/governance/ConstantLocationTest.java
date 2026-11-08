package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A literal value that a module names as a constant is named in one file of that module, so a fact has one
 * authoritative location and the other files refer to it instead of declaring a copy that drifts.
 *
 * <p>The production sources of every module with production Java and of every npm package are read, each module on
 * its own, because a constant cannot be shared across a module boundary without a dependency. A named constant is a
 * Java {@code static final} {@link String} or a top-level TypeScript {@code const} in upper case, each initialized
 * with one string literal of at least {@value #MINIMUM_LENGTH} characters, so short structural values such as a
 * separator stay out. A value that two files of one module declare is reported unless an exemption names that module
 * and value. An
 * exemption is for a coincidence: one word that two unrelated formats happen to share, which stays apart because the
 * formats change apart. An exemption whose value no longer repeats is reported too, so the list cannot outlive the
 * code it describes.
 */
class ConstantLocationTest {

    private static final int MINIMUM_LENGTH = 6;

    private static final Pattern JAVA_CONSTANT = Pattern.compile(
        "\\bstatic\\s+final\\s+String\\s+(\\w+)\\s*=\\s*\"((?:[^\"\\\\\\n]|\\\\.)*)\"\\s*;"
    );

    private static final Pattern NPM_CONSTANT = Pattern.compile(
        "^(?:export\\s+)?const\\s+([A-Z][A-Z0-9_]*)(?:\\s*:\\s*[^=\\n]+)?\\s*=\\s*"
            + "(['\"])((?:(?!\\2)[^\\\\\\n]|\\\\.)*)\\2(?:\\s+as\\s+const)?\\s*;?\\s*$",
        Pattern.MULTILINE
    );

    private static final String JAVA_SOURCES = "src/main/java/";
    private static final String NPM_SOURCES = "src/";
    private static final String JAVA_SUFFIX = ".java";
    private static final String NPM_SUFFIX = ".ts";

    private static final String GOVERNANCE = "airness-governance";
    private static final String PLUGIN = "airness-maven-plugin";

    private static final List<Exemption> EXEMPTIONS = List.of(
        new Exemption(
            GOVERNANCE, ".properties",
            "the extension of message bundles and of Spring configuration, two file kinds read by unrelated rules"
        ),
        new Exemption(
            GOVERNANCE, "description", "a field of the secret scan configuration and a field of a skill manifest"
        ),
        new Exemption(
            PLUGIN, "airness", "the artifactId of the aggregator and a directory name in the build output"
        ),
        new Exemption(PLUGIN, "failed", "a summary field of a Checkov report and a verdict of a node gate result"),
        new Exemption(PLUGIN, "version", "a key of a node toolchain pin and a field of an SBOM component"),
        new Exemption(
            "airness-node", "AIRNESS_CLOCK",
            "the clock variable, which the clock preload declares itself because every process of a verification"
                + " tree imports it, and importing it installs the fixed clock"
        )
    );

    @Test
    void namesEachConstantValueInOneFileOfItsModule() {
        Path root = SelfModules.repository();
        Map<String, Map<String, Set<String>>> declared = declarations(root);
        assertTrue(
            declared.containsKey(GOVERNANCE) && declared.size() > 2,
            "a guard that read no module, or not this one, proves nothing"
        );
        assertEquals(
            List.of(), repeated(declared),
            "declare the value once and refer to it, or exempt a coincidence of two unrelated formats with a reason"
        );
        assertEquals(
            List.of(),
            EXEMPTIONS.stream()
                .filter(exemption -> filesDeclaring(declared, exemption).size() < 2)
                .map(Exemption::describe)
                .toList(),
            "an exemption whose value no longer repeats is stale; remove it from the list"
        );
    }

    @Test
    void readsEveryJavaSpellingOfAStringConstant() {
        String source = """
            private static final String FIRST = "first value";
            static final String SECOND = "second value";
            public static final String THIRD
                = "third value";
            private static final String ESCAPED = "a \\"quoted\\" value";
            private static final String JOINED = "joined " + "value";
            private final String instance = "instance value";
            """;
        assertEquals(
            Set.of("first value", "second value", "third value", "a \\\"quoted\\\" value"),
            values(JAVA_CONSTANT, source, 2)
        );
    }

    @Test
    void readsEveryTypeScriptSpellingOfATopLevelConstant() {
        String source = """
            const FIRST = 'first value';
            export const SECOND = "second value";
            export const THIRD: string = 'third value';
            const FOURTH = 'fourth value' as const;
            const FIFTH = 'fifth value'
            const lowerCase = 'lower value';
              const NESTED = 'nested value';
            const JOINED = `joined ${value}`;
            """;
        assertEquals(
            Set.of("first value", "second value", "third value", "fourth value", "fifth value"),
            values(NPM_CONSTANT, source, 3)
        );
    }

    @Test
    void reportsAValueTwoFilesOfOneModuleDeclare() {
        Map<String, Map<String, Set<String>>> declared = Map.of(
            "module-a", Map.of("package.json", Set.of("First.java", "Second.java"), "single", Set.of("First.java")),
            "module-b", Map.of("package.json", Set.of("Third.java"))
        );
        assertEquals(List.of("module-a: \"package.json\" in First.java, Second.java"), repeated(declared));
    }

    @Test
    void allowsAnExemptedCoincidenceOnlyInItsModule() {
        Map<String, Map<String, Set<String>>> declared = Map.of(
            PLUGIN, Map.of("failed", Set.of("CheckovReport.java", "NodeGateResult.java")),
            GOVERNANCE, Map.of("failed", Set.of("First.java", "Second.java"))
        );
        assertEquals(List.of(GOVERNANCE + ": \"failed\" in First.java, Second.java"), repeated(declared));
    }

    private static Map<String, Map<String, Set<String>>> declarations(Path root) {
        Map<String, Map<String, Set<String>>> declared = new TreeMap<>();
        List<Path> files = Repository.trackedFiles(root);
        Stream.concat(
            SelfModules.withProductionJava().stream().map(module -> new Sources(module, JAVA_SOURCES, JAVA_SUFFIX)),
            SelfModules.withNpmSources().stream().map(module -> new Sources(module, NPM_SOURCES, NPM_SUFFIX))
        ).forEach(sources -> files.stream().filter(sources::holds).forEach(file -> declare(declared, sources, file)));
        return declared;
    }

    private static void declare(Map<String, Map<String, Set<String>>> declared, Sources sources, Path file) {
        String text = Repository.readText(file).orElse("");
        String module = String.valueOf(sources.module().getFileName());
        String name = sources.module().relativize(file).toString().replace('\\', '/');
        Set<String> values = JAVA_SUFFIX.equals(sources.suffix())
            ? values(JAVA_CONSTANT, text, 2)
            : values(NPM_CONSTANT, text, 3);
        values.forEach(
            value -> declared.computeIfAbsent(module, _ -> new TreeMap<>())
                .computeIfAbsent(value, _ -> new TreeSet<>())
                .add(name)
        );
    }

    private static Set<String> values(Pattern pattern, String text, int group) {
        Matcher matcher = pattern.matcher(text);
        return matcher.results()
            .map(result -> result.group(group))
            .filter(value -> value.length() >= MINIMUM_LENGTH)
            .collect(Collectors.toSet());
    }

    private static List<String> repeated(Map<String, Map<String, Set<String>>> declared) {
        return declared.entrySet().stream().flatMap(ConstantLocationTest::repeatedIn).sorted().toList();
    }

    private static Stream<String> repeatedIn(Map.Entry<String, Map<String, Set<String>>> module) {
        return module.getValue().entrySet().stream()
            .filter(value -> value.getValue().size() > 1)
            .filter(value -> !exempt(module.getKey(), value.getKey()))
            .map(value -> describe(module.getKey(), value.getKey(), value.getValue()));
    }

    private static boolean exempt(String module, String value) {
        return EXEMPTIONS.stream().anyMatch(exemption -> exemption.covers(module, value));
    }

    private static Set<String> filesDeclaring(Map<String, Map<String, Set<String>>> declared, Exemption exemption) {
        return declared.getOrDefault(exemption.module(), Map.of()).getOrDefault(exemption.value(), Set.of());
    }

    private static String describe(String module, String value, Set<String> files) {
        String names = files.stream()
            .map(file -> file.substring(file.lastIndexOf('/') + 1))
            .sorted()
            .collect(Collectors.joining(", "));
        return module + ": \"" + value + "\" in " + names;
    }

    /**
     * The production sources of one module in one language.
     *
     * @param module the module directory
     * @param root   the source root, relative to the module
     * @param suffix the file name suffix of a source file
     */
    private record Sources(Path module, String root, String suffix) {

        boolean holds(Path file) {
            return file.startsWith(this.module.resolve(this.root)) && file.toString().endsWith(this.suffix);
        }
    }

    /**
     * A value two files of one module may both declare, because it is a coincidence of unrelated formats.
     *
     * @param module the module directory name
     * @param value  the repeated literal value
     * @param reason why the two declarations stay apart
     */
    private record Exemption(String module, String value, String reason) {

        boolean covers(String candidateModule, String candidateValue) {
            return this.module.equals(candidateModule) && this.value.equals(candidateValue);
        }

        String describe() {
            return this.module + ": \"" + this.value + "\"";
        }
    }
}
