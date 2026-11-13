package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A stack is named only inside the paths it owns and at the registration points listed here, so the stack can be
 * removed by deleting those paths and the registration points, and the core stays generic.
 *
 * <p>Every committable file outside the stack's own paths is read. A stack token is every word or identifier that
 * holds {@code payload} or {@code ploaness} in any case, whatever its case style, so {@code PayloadStack},
 * {@code PAYLOAD_NODE}, {@code payloadConfig}, {@code prepare_payload_packages} and the bare word are all tokens,
 * read together with an npm scope before it and a dotted name after it. A token is reported unless an entry names
 * that file and that exact token. There are two lists of entries. A registration point names the stack as data, and
 * removing the stack deletes it. A generic use is the ordinary word, such as the body of a request or the data of a
 * PNG chunk, which says nothing about the stack and stays when the stack goes. An entry whose token no longer
 * occurs is reported too, and so is a stack path nothing is committed under, so neither list can widen silently or
 * outlive the code it describes. The registration list is the canonical set of registration points. Removing the
 * stack deletes every entry of the stack-path and registration lists, and the names only those entries read, which
 * leaves both lists empty and the guard whole.
 */
class StackConfinementTest {

    private static final Pattern STACK_TOKEN = Pattern.compile(
        "(?:@[\\w-]+/)?[\\w-]*(?i:payload|ploaness)[\\w-]*(?:\\.[\\w-]+)*"
    );

    private static final String NPM_PACKAGE = "@airness/payload";
    private static final String WORD = "payload";
    private static final String PLURAL = "payloads";

    private static final String GOVERNANCE = "airness-governance/src/main/java/eu/ciechanowiec/airness/governance/";
    private static final String GOVERNANCE_TESTS
        = "airness-governance/src/test/java/eu/ciechanowiec/airness/governance/";

    // The names only the entries of the stack-path and registration lists read, deleted with those entries.
    private static final String PARENT = "airness-parent-payload";
    private static final String NODE_MODULE = "airness-payload-node";
    private static final String ASSETS_MODULE = "airness-payload-assets";
    private static final String LAYER = "PAYLOAD";
    private static final String PRODUCT = "Payload";
    private static final String PLUGIN = "airness-maven-plugin/src/main/java/eu/ciechanowiec/airness/maven/";
    private static final String PLUGIN_TESTS = "airness-maven-plugin/src/test/java/eu/ciechanowiec/airness/maven/";

    private static final String SPRING_REQUEST = "a Spring request parameter or response body in a fixture";
    private static final String PNG_CHUNK = "the data a PNG chunk carries";
    private static final String WRITING_GUIDELINE = "README-guideline-writing.adoc";
    private static final String GUIDELINE_EXAMPLES
        = "the HTTP and JSON bodies the examples of the writing guideline send";

    /**
     * The paths the stack owns whole, which removing it deletes: its modules, its integration domain and the one
     * test per core module that holds its registration.
     */
    private static final List<String> STACK_PATHS = List.of(
        "airness-payload-assets/", "airness-parent-payload/", "airness-payload-node/", "airness-it/cases/payload.sh",
        "airness-it/fixtures/payload/", GOVERNANCE_TESTS + "PayloadRegistrationTest.java",
        PLUGIN_TESTS + "PayloadRegistrationTest.java", "airness-node/tests/payload-registration.spec.ts"
    );

    /**
     * This guard, which spells every token it guards.
     */
    private static final String GUARD = GOVERNANCE_TESTS + "StackConfinementTest.java";

    private static final List<Entry> REGISTRATION_POINTS = List.of(
        new Entry(
            "pom.xml", Set.of(ASSETS_MODULE, PARENT, NODE_MODULE),
            "the reactor modules, the npm-only module kept out of Central, and its dist kept from the editor check"
        ),
        new Entry(".gitignore", Set.of(NODE_MODULE), "the .npmrc the stack's local registry writes"),
        new Entry(
            "README.adoc",
            Set.of(
                NPM_PACKAGE, PRODUCT, WORD, PARENT, ASSETS_MODULE, NODE_MODULE, "agent-guide-payload.md",
                "airnessPayload.publicAccess", "airnessPayload.localStorage", "payload-blocklist", "payload-defaults",
                "payload-environment", "payload-generated", "payload-migrations", "payload-source-rules",
                "payload-stack", "payload.sh", "payloadcms.com"
            ),
            "the Payload stack section: what it adds, how to attach it and how to remove it"
        ),
        new Entry(
            "AGENTS.md", Set.of(NPM_PACKAGE, PRODUCT, WORD, PARENT, ASSETS_MODULE, NODE_MODULE),
            "the project text naming the stack's modules and its integration domain"
        ),
        new Entry(".adocent/vocabulary.txt", Set.of(PRODUCT), "the product name the stack's documentation spells"),
        new Entry(
            ".github/workflows/release.yml", Set.of(NODE_MODULE, NPM_PACKAGE),
            "the stack's npm package in the npm publication order, and the lockfile whose node package integrity is"
                + " the one line a release commit may change beyond the version"
        ),
        new Entry(
            "airness-it/verify.sh",
            Set.of(
                WORD, "payload.sh", "payload_package", "payload_mismatched_package", "prepare_payload_packages",
                "run_payload_cases"
            ),
            "the payload integration domain: its name, its case file, its lane and the packages its registry serves"
        ),
        new Entry(
            ".claude/settings.json", Set.of(NODE_MODULE),
            "the deny rules assets-sync wrote for the managed files of the stack's node project; removing the stack"
                + " deletes them by hand, because assets-sync never removes a rule"
        ),
        new Entry(
            GOVERNANCE + "Layer.java", Set.of(LAYER, PARENT),
            "the layer that selects the stack's files, with the node layer it refines"
        ),
        new Entry(
            PLUGIN + "RepositorySites.java", Set.of(LAYER, PARENT), "the parent whose modules carry the layer"
        ),
        new Entry(PLUGIN + "RepositoryProjects.java", Set.of(PARENT), "a parent Airness publishes"),
        new Entry(
            PLUGIN + "HarnessVersions.java", Set.of(PARENT, NPM_PACKAGE),
            "the parent that names the version of the stack's npm package"
        ),
        new Entry(
            "airness-node/src/layer.ts", Set.of(NPM_PACKAGE, NODE_MODULE),
            "the trusted layer package and the one module that builds it"
        ),
        new Entry(
            "airness-node/src/layer-lint-policy.ts",
            Set.of(NPM_PACKAGE, PRODUCT, WORD, "payload-source-rules", "payload.config.ts"),
            "the node rules the stack's ESLint blocks may relax, with the files and reasons"
        ),
        new Entry(
            GOVERNANCE_TESTS + "ProjectFiles.java", Set.of(PARENT, NODE_MODULE),
            "the modules that inherit a published parent rather than the aggregator"
        ),
        new Entry(
            GOVERNANCE_TESTS + "NodePackageVersionTest.java", Set.of(NODE_MODULE),
            "the npm packages held to the reactor version"
        )
    );

    private static final List<Entry> GENERIC_USES = List.of(
        new Entry(WRITING_GUIDELINE, Set.of(WORD, PLURAL), GUIDELINE_EXAMPLES),
        new Entry(
            "airness-starters/spring-boot/" + WRITING_GUIDELINE, Set.of(WORD, PLURAL),
            GUIDELINE_EXAMPLES + ", in the copy the starter's own README follows"
        ),
        new Entry(
            "airness-config/src/main/resources/eu/ciechanowiec/airness/static_code_analysis/checkstyle.xml",
            Set.of(WORD), "the request body and request parts a Spring handler validates"
        ),
        new Entry(GOVERNANCE + "PngBytes.java", Set.of("PAYLOAD_OFFSET", WORD), PNG_CHUNK),
        new Entry(
            GOVERNANCE + "SpringConfigurationRules.java", Set.of(WORD), "the body a health endpoint answers with"
        ),
        new Entry(
            GOVERNANCE + "TemplateMessageEvidence.java", Set.of(WORD, "payload.split"),
            "the text of one evidence manifest line"
        ),
        new Entry(
            GOVERNANCE + "TemplateMessageReference.java", Set.of(WORD), "the text of one evidence manifest line"
        ),
        new Entry(GOVERNANCE_TESTS + "CheckstyleVariantTest.java", Set.of(WORD, "payload.toString"), SPRING_REQUEST),
        new Entry(GOVERNANCE_TESTS + "ImageBytesTest.java", Set.of(WORD), PNG_CHUNK),
        new Entry(GOVERNANCE_TESTS + "ImageFixtures.java", Set.of(WORD, "payload.length"), PNG_CHUNK),
        new Entry(GOVERNANCE_TESTS + "RequestMapScopeCheckstyleTest.java", Set.of(WORD), SPRING_REQUEST),
        new Entry(GOVERNANCE_TESTS + "SpringRequestMapCheckstyleTest.java", Set.of(WORD), SPRING_REQUEST),
        new Entry(GOVERNANCE_TESTS + "SpringViewDeclarationsTest.java", Set.of(WORD), SPRING_REQUEST),
        new Entry(GOVERNANCE_TESTS + "SpringViewReturnsTest.java", Set.of(WORD), SPRING_REQUEST),
        new Entry(
            GOVERNANCE_TESTS + "TreeFingerprintTest.java", Set.of(WORD), "the content of a file the fingerprint reads"
        ),
        new Entry("airness-it/cases/request-maps.sh", Set.of(WORD), SPRING_REQUEST),
        new Entry("airness-it/cases/spring.sh", Set.of(WORD), SPRING_REQUEST),
        new Entry("airness-node/src/file-roles.ts", Set.of(PLURAL), "binary content after a text header"),
        new Entry("airness-node/tests/file-roles.spec.ts", Set.of("0payload"), "binary content after a NUL byte"),
        new Entry(
            "airness-node/tests/fixtures/cyclonedx/bom-1.6.schema.json", Set.of(PLURAL),
            "the published CycloneDX schema, which speaks of attachment payloads"
        ),
        new Entry(
            "airness-node/tests/source-text.spec.ts", Set.of("payload.find"),
            "an example call expression whose punctuation the needle escapes"
        )
    );

    @Test
    void namesTheStackOnlyAtItsRegistrationPoints() {
        Path root = SelfModules.repository();
        List<String> committed = Repository.trackedFiles(root).stream().map(file -> relative(root, file)).toList();
        List<String> scanned = committed.stream()
            .filter(path -> !GUARD.equals(path) && STACK_PATHS.stream().noneMatch(path::startsWith))
            .toList();
        assertTrue(
            scanned.contains("pom.xml"), "a guard that read nothing, or not the root project file, proves nothing"
        );
        Map<String, Set<String>> found = scanned.stream().collect(
            Collectors.toMap(path -> path, path -> tokens(root.resolve(path)), (first, _) -> first, TreeMap::new)
        );
        assertEquals(
            List.of(), violations(found),
            "word the core generically, or list the file as a registration point or a generic use with the token"
        );
        assertEquals(
            List.of(), entries().flatMap(entry -> stale(entry, found)).sorted().toList(),
            "an entry whose token no longer occurs is stale; remove it from its list"
        );
        List<String> vacant = STACK_PATHS.stream()
            .filter(path -> committed.stream().noneMatch(file -> file.startsWith(path)))
            .toList();
        assertEquals(List.of(), vacant, "a stack path nothing is committed under is stale; remove it from the list");
    }

    @Test
    void readsEveryWordOrIdentifierHoldingTheStackName() {
        assertEquals(
            Set.of("PayloadStack", "PAYLOAD_NODE", "payloadConfig", "prepare_payload_packages", "ploanessGate"),
            tokensOf(
                "class PayloadStack { int PAYLOAD_NODE; Object payloadConfig; }\n"
                    + "prepare_payload_packages ploanessGate;"
            )
        );
    }

    @Test
    void readsTheBareDomainWordOfTheIntegrationSuite() {
        String domains = "''|maven|node|browser|payload)\n        ;;\n    payload)\n        run_payload_cases\n";
        assertEquals(Set.of(WORD, "run_payload_cases"), tokensOf(domains));
        assertEquals(
            List.of("airness-it/cases/maven.sh: payload", "airness-it/cases/maven.sh: run_payload_cases"),
            violations(Map.of("airness-it/cases/maven.sh", tokensOf(domains)))
        );
    }

    @Test
    void keepsAnNpmScopeAndADottedNameWithTheirToken() {
        assertEquals(
            Set.of(NPM_PACKAGE, "payload.config.ts", WORD),
            tokensOf("import '@airness/payload/eslint' beside src/payload.config.ts, the payload.")
        );
    }

    @Test
    void allowsAGenericUseOnlyInTheFileThatListsIt() {
        Set<String> chunk = tokensOf("private static final int PAYLOAD_OFFSET = LENGTH_BYTES + TYPE_BYTES;");
        assertEquals(Set.of("PAYLOAD_OFFSET"), chunk);
        String elsewhere = GOVERNANCE + "ImageBytes.java";
        assertEquals(
            List.of(elsewhere + ": PAYLOAD_OFFSET"),
            violations(Map.of(GOVERNANCE + "PngBytes.java", chunk, elsewhere, chunk))
        );
    }

    private static Stream<Entry> entries() {
        return Stream.concat(REGISTRATION_POINTS.stream(), GENERIC_USES.stream());
    }

    private static List<String> violations(Map<String, Set<String>> found) {
        Map<String, Set<String>> allowed = entries().collect(
            Collectors.toMap(Entry::path, Entry::tokens, StackConfinementTest::union)
        );
        return found.entrySet().stream()
            .flatMap(file -> unlisted(file.getKey(), file.getValue(), allowed))
            .sorted()
            .toList();
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> both = new HashSet<>(first);
        both.addAll(second);
        return Set.copyOf(both);
    }

    private static Stream<String> unlisted(String path, Set<String> tokens, Map<String, Set<String>> allowed) {
        Set<String> listed = allowed.getOrDefault(path, Set.of());
        return tokens.stream().filter(token -> !listed.contains(token)).map(token -> path + ": " + token);
    }

    private static Stream<String> stale(Entry entry, Map<String, Set<String>> found) {
        Set<String> present = found.getOrDefault(entry.path(), Set.of());
        return entry.tokens().stream()
            .filter(token -> !present.contains(token))
            .map(token -> entry.path() + ": " + token);
    }

    private static Set<String> tokens(Path file) {
        return Repository.readText(file).map(StackConfinementTest::tokensOf).orElse(Set.of());
    }

    private static Set<String> tokensOf(String text) {
        return STACK_TOKEN.matcher(text).results().map(MatchResult::group).collect(Collectors.toSet());
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }

    /**
     * A file that names the stack, the exact tokens it may name, and why it is the place to name them.
     *
     * @param path   the repository-relative path
     * @param tokens the stack tokens the file may contain, each of which it must contain
     * @param reason why the file names them
     */
    private record Entry(String path, Set<String> tokens, String reason) {
    }
}
