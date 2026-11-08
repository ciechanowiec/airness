package eu.ciechanowiec.airness.governance;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipFile;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;

/**
 * The property table read against the plugins it describes, so a parameter a plugin upgrade adds is
 * classified before the upgrade ships rather than found open by the first project to set it.
 *
 * <p>Every goal the parents bind is read from the pinned descriptor, together with each goal it forks, since
 * a forked goal runs under the forking execution and reads properties of its own. Every editable parameter
 * whose value is a single user property is a row the table must hold, and the table may hold no other row.
 */
class PluginDescriptorPropertiesTest {

    private static final Path DESCRIPTORS = Path.of("target", "plugin-descriptors");
    private static final String DESCRIPTOR = "META-INF/maven/plugin.xml";
    private static final String AIRNESS_PLUGIN = "airness-maven-plugin";
    // Every parent Airness publishes, found by its directory so a parent a stack adds is read without a list.
    private static final String PARENT_PREFIX = "airness-parent";
    private static final Pattern EXPRESSION = Pattern.compile("\\$\\{([^}]+)}");
    /*
     * What Maven itself answers rather than a property a project can set. Every other name, maven.test.skip and
     * maven.compiler.release among them, is a user property, whatever its prefix suggests.
     */
    private static final Pattern BUILT_IN = Pattern.compile(
        "^(?:(?:project|session|settings|mojoExecution|mojo|plugin|reactorProjects|localRepository|basedir"
            + "|executedProject|repositorySystemSession|pluginDescriptor|env)(?:\\.|$)"
            + "|maven\\.(?:home|version|build\\.))"
    );
    // The goals jar packaging binds without an execution in any parent, which inherit the plugin-level setup.
    private static final Map<String, List<String>> PACKAGING = Map.of(
        "maven-compiler-plugin", List.of("compile", "testCompile"),
        "maven-surefire-plugin", List.of("test")
    );
    /*
     * Managed plugins whose goals decide no verdict, so no property of theirs can turn a failing check green.
     * The Airness plugin decides verdicts and is read by its own module, whose build writes its descriptor.
     */
    private static final Map<String, String> NO_VERDICT = Map.ofEntries(
        entry("maven-clean-plugin", "removes the build directory before anything is checked"),
        entry("maven-resources-plugin", "copies resources that the checks then read from the build"),
        entry("maven-jar-plugin", "packages the classes the checks have already read"),
        entry("maven-install-plugin", "copies the finished artifact to the local repository"),
        entry("maven-deploy-plugin", "publishes the finished artifact"),
        entry("maven-shade-plugin", "repackages the artifact the project chose to build"),
        entry("maven-assembly-plugin", "writes the distributions the project describes"),
        entry("maven-source-plugin", "packages sources for publication"),
        entry("maven-javadoc-plugin", "packages documentation for publication"),
        entry("maven-site-plugin", "renders a site no check reads"),
        entry("exec-maven-plugin", "runs a step the project binds for itself"),
        entry("build-helper-maven-plugin", "adds roots the checks then read like any other"),
        entry("download-maven-plugin", "fetches an input the project binds for itself"),
        entry("maven-gpg-plugin", "signs the finished artifact"),
        entry("central-publishing-maven-plugin", "publishes the finished artifact"),
        entry("versions-maven-plugin", "reports versions on request and is bound by no parent"),
        entry("spring-boot-maven-plugin", "repackages the archive the checks have already read")
    );

    @Test
    void classifiesEveryPropertyOfEveryGoalTheParentsBind() {
        SortedSet<String> derived = verdictPlugins().stream()
            .flatMap(PluginDescriptorPropertiesTest::properties)
            .collect(Collectors.toCollection(TreeSet::new));
        SortedSet<String> listed = PluginProperties.rows().stream()
            .map(row -> row.plugin() + '\t' + row.property())
            .collect(Collectors.toCollection(TreeSet::new));
        assertAll(
            () -> assertEquals(
                Set.of(), difference(derived, listed),
                "add these rows to plugin-properties.tsv, RESERVED unless the property decides no verdict"
            ),
            () -> assertEquals(
                Set.of(), difference(listed, derived),
                "remove these rows from plugin-properties.tsv, since no bound goal reads them any longer"
            )
        );
    }

    @Test
    void reservesEveryPropertyOfAPluginAirnessSupplies() {
        Set<String> supplied = ManagedVersions.coordinates().stream()
            .filter(ManagedVersions.Coordinate::supplied)
            .map(ManagedVersions.Coordinate::artifact)
            .collect(Collectors.toUnmodifiableSet());
        assertEquals(
            List.of(),
            PluginProperties.rows().stream()
                .filter(row -> supplied.contains(row.plugin()) && row.use() == PluginProperties.Use.PROJECT)
                .toList(),
            "a project cannot configure a plugin Airness supplies, so a property of one has no use but changing "
                + "how the harness runs it"
        );
    }

    @Test
    void decidesForEveryManagedPluginWhetherItsGoalsDecideAVerdict() {
        Set<String> managed = plugins().collect(Collectors.toCollection(TreeSet::new));
        assertAll(
            () -> assertEquals(
                Set.of(), difference(NO_VERDICT.keySet(), managed),
                "a plugin passed over here is one the harness still manages"
            ),
            () -> assertEquals(
                List.of(),
                verdictPlugins().stream().filter(plugin -> !Files.isRegularFile(descriptor(plugin))).toList(),
                "copy the descriptor of each of these in the airness-self-plugin-descriptors execution"
            )
        );
    }

    private static List<String> verdictPlugins() {
        return plugins().filter(plugin -> !NO_VERDICT.containsKey(plugin)).sorted().toList();
    }

    private static Stream<String> plugins() {
        return ManagedVersions.coordinates().stream()
            .filter(coordinate -> coordinate.kind() == ManagedVersions.Kind.PLUGIN)
            .map(ManagedVersions.Coordinate::artifact)
            .filter(artifact -> !AIRNESS_PLUGIN.equals(artifact))
            .distinct();
    }

    private static Stream<String> properties(String plugin) {
        Map<String, Element> mojos = mojos(plugin);
        return reached(goals(plugin), mojos).stream()
            .map(mojos::get)
            .flatMap(PluginDescriptorPropertiesTest::propertiesOf)
            .distinct()
            .map(property -> plugin + '\t' + property);
    }

    // The bound goals and every goal one of them forks, read until nothing new is forked.
    private static Set<String> reached(Set<String> bound, Map<String, Element> mojos) {
        Set<String> reached = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>(bound);
        while (!pending.isEmpty()) {
            String goal = pending.pop();
            if (reached.add(goal)) {
                Xml.text(mojo(mojos, goal), "executeGoal").ifPresent(pending::push);
            }
        }
        return reached;
    }

    private static Element mojo(Map<String, Element> mojos, String goal) {
        return Optional.ofNullable(mojos.get(goal))
            .orElseThrow(() -> new IllegalStateException("no goal " + goal + " in the descriptor"));
    }

    private static Stream<String> propertiesOf(Element mojo) {
        Set<String> editable = Xml.firstChild(mojo, "parameters").stream()
            .flatMap(parameters -> Xml.children(parameters, "parameter").stream())
            .filter(parameter -> !"false".equals(Xml.text(parameter, "editable").orElse("true")))
            .flatMap(parameter -> Xml.text(parameter, "name").stream())
            .collect(Collectors.toUnmodifiableSet());
        return Xml.firstChild(mojo, "configuration").stream()
            .flatMap(ProjectFiles::elementChildren)
            .filter(value -> editable.contains(value.getTagName()))
            .flatMap(value -> single(value.getTextContent().strip()).stream())
            .filter(property -> !BUILT_IN.matcher(property).find());
    }

    private static Optional<String> single(String value) {
        Matcher matcher = EXPRESSION.matcher(value);
        return matcher.matches() ? Optional.of(matcher.group(1)) : Optional.empty();
    }

    private static Set<String> goals(String plugin) {
        Map<String, Set<String>> values = propertyValues();
        Stream<String> declared = poms()
            .flatMap(pom -> ProjectFiles.descendants(pom, "plugin"))
            .filter(element -> Xml.text(element, "artifactId").map(plugin::equals).orElse(false))
            .flatMap(element -> ProjectFiles.descendants(element, "goal"))
            .map(goal -> goal.getTextContent().strip())
            .flatMap(
                goal -> single(goal).map(name -> values.getOrDefault(name, Set.of()).stream()).orElseGet(
                    () -> Stream.of(goal)
                )
            );
        return Stream.concat(declared, PACKAGING.getOrDefault(plugin, List.of()).stream())
            .collect(Collectors.toUnmodifiableSet());
    }

    // Every value a property takes in the parents, a profile's included, so a goal named by one is read in full.
    private static Map<String, Set<String>> propertyValues() {
        return poms()
            .flatMap(pom -> ProjectFiles.descendants(pom, "properties"))
            .flatMap(ProjectFiles::elementChildren)
            .collect(
                Collectors.groupingBy(
                    Element::getTagName,
                    Collectors.mapping(property -> property.getTextContent().strip(), Collectors.toSet())
                )
            );
    }

    private static Stream<Element> poms() {
        Path repository = SelfModules.repository();
        return Stream.concat(Stream.of(ProjectFiles.rootPom()), parentPoms(repository).stream())
            .map(ProjectFiles::document);
    }

    @SneakyThrows
    private static List<Path> parentPoms(Path repository) {
        try (Stream<Path> entries = Files.list(repository)) {
            return entries
                .filter(entry -> entry.getFileName().toString().startsWith(PARENT_PREFIX))
                .map(entry -> entry.resolve("pom.xml"))
                .filter(Files::isRegularFile)
                .sorted()
                .toList();
        }
    }

    @SneakyThrows
    private static Map<String, Element> mojos(String plugin) {
        try (ZipFile archive = new ZipFile(descriptor(plugin).toFile())) {
            String content = new String(
                archive.getInputStream(archive.getEntry(DESCRIPTOR)).readAllBytes(), StandardCharsets.UTF_8
            );
            Element root = Xml.parse(content).getDocumentElement();
            return ProjectFiles.descendants(root, "mojo")
                .collect(Collectors.toMap(mojo -> Xml.text(mojo, "goal").orElseThrow(), Function.identity()));
        }
    }

    private static Path descriptor(String plugin) {
        return DESCRIPTORS.resolve(plugin + ".jar");
    }

    private static Set<String> difference(Set<String> from, Set<String> without) {
        return from.stream().filter(item -> !without.contains(item)).collect(Collectors.toCollection(TreeSet::new));
    }
}
