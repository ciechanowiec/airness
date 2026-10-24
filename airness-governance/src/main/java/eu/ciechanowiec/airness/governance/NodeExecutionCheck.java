package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * No build step a module declares runs a Node executable by name, so the Node a build reaches is the one the
 * harness pinned and verified.
 *
 * <p>An exec-maven-plugin execution whose executable is {@code node}, {@code npm}, {@code npx}, {@code pnpm},
 * {@code yarn}, {@code bun} or {@code deno} runs whatever the machine's {@code PATH} holds, and so does an
 * {@code exec} or {@code apply} task of maven-antrun-plugin naming one, and so does the {@code exec.executable}
 * property the exec plugin reads when an execution names no executable. That is the problem the refusal of
 * frontend-maven-plugin solves, reached another way. The raw pom is read, inactive profiles and plugin
 * management included, for the reason {@link BlocklistCheck} reads it.
 *
 * <p>This is the exact limit of the guarantee: what an allowed process starts is not inspected. An execution
 * running {@code sh build.sh} passes, and so does a script calling a Node by an absolute path.
 */
public final class NodeExecutionCheck {

    private static final String HEADLINE = "Build steps that run a Node executable beside the one the harness verifies";
    private static final Set<String> EXECUTABLES = Set.of("node", "npm", "npx", "pnpm", "yarn", "bun", "deno");
    private static final Set<String> SUFFIXES = Set.of(".exe", ".cmd");
    private static final Set<String> ANT_TASKS = Set.of("exec", "apply");
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{([^}]+)}");
    private static final String EXEC = "exec-maven-plugin";
    private static final String ANTRUN = "maven-antrun-plugin";
    private static final String CONFIGURATION = "configuration";
    private static final String EXECUTABLE = "executable";
    private static final String EXEC_PROPERTY = "exec.executable";
    private static final String REPAIR = ", which is whatever the machine's PATH holds rather than the Node the "
        + "harness verifies; use the airness:node-run goal, which runs a package.json script on the harness's Node";

    private final List<String> offences;

    /**
     * Reads the raw pom of one module.
     *
     * @param root       the working tree root, which the offences are named relative to
     * @param pom        the raw pom of the module
     * @param properties the effective properties of the module, for an executable written as one
     */
    public NodeExecutionCheck(Path root, Path pom, Map<String, String> properties) {
        String location = root.relativize(pom).toString();
        Element project = Xml.parse(read(pom)).getDocumentElement();
        Stream<String> plugins = DeclaredCoordinates.plugins(project)
            .map(Element.class::cast)
            .flatMap(plugin -> steps(plugin, properties));
        this.offences = Stream.concat(plugins, property(project, properties))
            .map(step -> location + ": " + step + REPAIR)
            .distinct()
            .toList();
    }

    /**
     * Every declared step that runs a Node executable by name.
     *
     * @return the single verdict this check produces
     */
    public List<Findings> findings() {
        return List.of(new Findings(HEADLINE, this.offences));
    }

    private static Stream<String> steps(Element plugin, Map<String, String> properties) {
        String artifact = Xml.text(plugin, "artifactId").orElse("");
        return configurations(plugin).flatMap(
            configured -> executables(artifact, configured.getValue())
                .flatMap(executable -> named(executable, properties).stream())
                .map(executable -> artifact + ' ' + configured.getKey() + " runs " + executable)
        );
    }

    // The exec plugin names its executable in its configuration, and an Ant target names one on each task.
    private static Stream<String> executables(String artifact, Element configuration) {
        if (EXEC.equals(artifact)) {
            return Xml.text(configuration, EXECUTABLE).stream();
        }
        return ANTRUN.equals(artifact) ? tasks(configuration) : Stream.empty();
    }

    // The exec plugin also reads its executable from a property, which a pom may set in any profile or
    // inherit from a parent, and which then reaches every exec execution that names none itself.
    private static Stream<String> property(Element project, Map<String, String> properties) {
        return Stream.concat(
            DeclaredCoordinates.propertyBlocks(project).flatMap(block -> Xml.text(block, EXEC_PROPERTY).stream()),
            Optional.ofNullable(properties.get(EXEC_PROPERTY)).stream()
        ).flatMap(executable -> named(executable, properties).stream())
            .map(executable -> "the property " + EXEC_PROPERTY + " runs " + executable);
    }

    // The plugin-level configuration reaches every execution, and each execution may carry its own.
    private static Stream<Map.Entry<String, Element>> configurations(Element plugin) {
        Stream<Map.Entry<String, Element>> shared = Xml.firstChild(plugin, CONFIGURATION).stream()
            .map(configuration -> Map.entry(CONFIGURATION, configuration));
        Stream<Map.Entry<String, Element>> executions = Xml.firstChild(plugin, "executions").stream()
            .flatMap(listed -> Xml.children(listed, "execution").stream())
            .flatMap(
                execution -> Xml.firstChild(execution, CONFIGURATION).stream()
                    .map(configuration -> Map.entry("execution " + Xml.idTextOrEmpty(execution), configuration))
            );
        return Stream.concat(shared, executions);
    }

    private static Stream<String> tasks(Element configuration) {
        NodeList descendants = configuration.getElementsByTagName("*");
        return IntStream.range(0, descendants.getLength())
            .mapToObj(descendants::item)
            .map(Element.class::cast)
            .filter(element -> ANT_TASKS.contains(element.getTagName()))
            .map(element -> element.getAttribute(EXECUTABLE))
            .filter(executable -> !executable.isBlank());
    }

    // The executable is judged by its file name, so a directory in front of it or a Windows suffix behind it
    // names the same tool.
    private static Optional<String> named(String written, Map<String, String> properties) {
        String resolved = resolve(written.strip(), properties);
        String file = resolved.substring(Math.max(resolved.lastIndexOf('/'), resolved.lastIndexOf('\\')) + 1)
            .toLowerCase(Locale.ROOT);
        String bare = SUFFIXES.stream()
            .filter(file::endsWith)
            .findFirst()
            .map(suffix -> file.substring(0, file.length() - suffix.length()))
            .orElse(file);
        return Optional.of(written.strip()).filter(_ -> EXECUTABLES.contains(bare));
    }

    private static String resolve(String written, Map<String, String> properties) {
        return PROPERTY.matcher(written).replaceAll(
            found -> Matcher.quoteReplacement(properties.getOrDefault(found.group(1), found.group()))
        );
    }

    private static String read(Path pom) {
        try {
            return Files.readString(pom);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + pom, exception);
        }
    }
}
