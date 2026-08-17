package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Rejects raw pom declarations that would weaken the effective inherited harness model.
 *
 * <p>The raw model is intentionally checked instead of only the active effective model. An override in
 * an inactive profile is still a dormant bypass, and the effective model loses the information that a
 * value came from the child rather than from Airness. Ordinary extension-plugin configuration remains
 * available. Only fields that select, skip, or replace Airness checks are protected.
 *
 * <p>A banned group is the one rule here that rejects a tool rather than a bypass. Airness ran mutation
 * analysis once and stopped, because the accepted-survivor baseline it needs costs per-mutant
 * bookkeeping whose verdicts turn on how loaded the machine was. A project that reintroduces the tool
 * reintroduces that cost into a harness with nothing to read its report, so the declaration is refused
 * where it is written rather than left to fail later with a report nobody consumes.
 */
@UtilityClass
public final class MavenModelPolicy {

    private static final String MAVEN_PLUGINS = "org.apache.maven.plugins";
    private static final Set<String> PROTECTED_PROPERTIES = Set.of(
        "airness.coverage.skip",
        "airness.enforce",
        "airness.rewrite.apply.phase",
        "airness.source.formatting.apply",
        "maven.test.skip",
        "skipTests"
    );
    private static final Map<String, Set<String>> PROTECTED_PLUGIN_CONFIGURATION = Map.of(
        "maven-compiler-plugin",
        Set.of(
            "annotationProcessorPaths", "compilerArgs", "failOnWarning", "fork", "proc", "release",
            "source", "target"
        ),
        "maven-surefire-plugin",
        Set.of(
            "configurationParameters", "excludedGroups", "excludes", "failIfNoSpecifiedTests",
            "failIfNoTests", "groups", "includes", "skip", "skipExec", "skipTests", "suiteXmlFiles",
            "test", "testFailureIgnore"
        ),
        "maven-enforcer-plugin",
        Set.of("fail", "failFast", "rules", "rulesToExecute", "rulesToSkip", "skip")
    );
    private static final Set<String> BANNED_GROUPS = Set.of("org.pitest");

    /**
     * Every child declaration that can weaken or make dependency resolution machine-specific.
     *
     * @param pom raw child pom
     * @return problems ordered by their message
     */
    public static List<String> problems(Path pom) {
        Element root = Xml.parse(read(pom)).getDocumentElement();
        return Stream.of(
            propertyProblems(root),
            PomPropertyOrder.problems(root).stream(),
            executionProblems(root),
            pluginConfigurationProblems(root),
            mergeOverrideProblems(root),
            systemDependencyProblems(root),
            bannedGroupProblems(root)
        ).flatMap(stream -> stream).sorted().toList();
    }

    private static Stream<String> bannedGroupProblems(Element root) {
        return Stream.of(
            DeclaredCoordinates.plugins(root),
            DeclaredCoordinates.dependencies(root),
            DeclaredCoordinates.paths(root)
        ).flatMap(stream -> stream)
            .filter(MavenModelPolicy::bannedGroup)
            .map(MavenModelPolicy::coordinate)
            .distinct()
            .map(coordinate -> "Remove " + coordinate + "; Airness runs no mutation analysis");
    }

    private static boolean bannedGroup(Node declaration) {
        return BANNED_GROUPS.contains(Xml.text(declaration, "groupId").orElse(""));
    }

    private static Stream<String> propertyProblems(Element root) {
        return DeclaredCoordinates.propertyBlocks(root)
            .flatMap(MavenModelPolicy::properties)
            .filter(PROTECTED_PROPERTIES::contains)
            .distinct()
            .map(property -> "Remove child property " + property + "; it can bypass the Airness verdict");
    }

    private static Stream<String> executionProblems(Element root) {
        return DeclaredCoordinates.plugins(root)
            .flatMap(MavenModelPolicy::executions)
            .map(execution -> Xml.text(execution, "id").orElse(""))
            .filter(id -> id.startsWith("airness-"))
            .distinct()
            .map(id -> "Remove child execution " + id + "; Airness owns every airness-* execution");
    }

    private static Stream<String> pluginConfigurationProblems(Element root) {
        return DeclaredCoordinates.plugins(root)
            .filter(MavenModelPolicy::mavenPlugin)
            .flatMap(MavenModelPolicy::protectedConfigurationProblems)
            .distinct();
    }

    private static Stream<String> mergeOverrideProblems(Element root) {
        return DeclaredCoordinates.plugins(root)
            .filter(MavenModelPolicy::mavenPlugin)
            .flatMap(MavenModelPolicy::mergeControls)
            .filter(element -> "override".equals(element.getAttribute("combine.self")))
            .map(
                element -> "Remove child " + element.getTagName()
                    + " combine.self=override; it discards inherited Airness configuration"
            )
            .distinct();
    }

    private static Stream<String> protectedConfigurationProblems(Node plugin) {
        String artifact = Xml.text(plugin, "artifactId").orElse("");
        Set<String> protectedNames = PROTECTED_PLUGIN_CONFIGURATION.getOrDefault(artifact, Set.of());
        return Xml.firstChild(plugin, "configuration").stream()
            .flatMap(configuration -> descendants(configuration).map(Element::getTagName))
            .filter(protectedNames::contains)
            .map(
                name -> "Remove child " + artifact + " configuration " + name
                    + "; it changes an Airness-owned check input"
            );
    }

    private static Stream<String> systemDependencyProblems(Element root) {
        return DeclaredCoordinates.dependencies(root)
            .filter(MavenModelPolicy::systemDependency)
            .map(MavenModelPolicy::coordinate)
            .distinct()
            .map(coordinate -> "Remove system-scoped dependency " + coordinate + "; use a repository coordinate");
    }

    private static boolean systemDependency(Node dependency) {
        return "system".equals(Xml.text(dependency, "scope").orElse(""))
            || Xml.firstChild(dependency, "systemPath").isPresent();
    }

    private static boolean mavenPlugin(Node plugin) {
        String group = Xml.text(plugin, "groupId").orElse(MAVEN_PLUGINS);
        return MAVEN_PLUGINS.equals(group)
            && PROTECTED_PLUGIN_CONFIGURATION.containsKey(Xml.text(plugin, "artifactId").orElse(""));
    }

    private static Stream<Node> executions(Node plugin) {
        return Xml.firstChild(plugin, "executions").stream()
            .flatMap(executions -> Xml.children(executions, "execution").stream())
            .map(Node.class::cast);
    }

    private static Stream<Element> configurations(Node plugin) {
        Stream<Element> pluginConfiguration = Xml.firstChild(plugin, "configuration").stream();
        Stream<Element> executionConfiguration = executions(plugin)
            .flatMap(execution -> Xml.firstChild(execution, "configuration").stream());
        return Stream.concat(pluginConfiguration, executionConfiguration);
    }

    private static Stream<Element> mergeControls(Node plugin) {
        Stream<Element> declaration = Stream.of((Element) plugin);
        Stream<Element> executions = Xml.firstChild(plugin, "executions").stream();
        return Stream.of(declaration, executions, configurations(plugin)).flatMap(stream -> stream);
    }

    private static Stream<Element> descendants(Element root) {
        return IntStream.range(0, root.getElementsByTagName("*").getLength())
            .mapToObj(index -> root.getElementsByTagName("*").item(index))
            .map(Element.class::cast);
    }

    private static Stream<String> properties(Node block) {
        return IntStream.range(0, block.getChildNodes().getLength())
            .mapToObj(index -> block.getChildNodes().item(index))
            .filter(node -> node.getNodeType() == Node.ELEMENT_NODE)
            .map(Element.class::cast)
            .map(Element::getTagName);
    }

    private static String coordinate(Node dependency) {
        return Xml.text(dependency, "groupId").orElse("") + ':'
            + Xml.text(dependency, "artifactId").orElse("");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + path, exception);
        }
    }
}
