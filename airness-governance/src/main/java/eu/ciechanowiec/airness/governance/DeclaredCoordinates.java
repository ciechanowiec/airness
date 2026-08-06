package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Reads every directly declared dependency, plugin, and parent out of a pom, resolving each exact
 * {@code ${property}} version against properties in that pom.
 *
 * <p>The scan is deliberately independent of Maven's effective model. Management sections, plugin
 * classpaths, annotation-processor paths, reporting, and inactive profiles are all sources of a pinned
 * coordinate, and model interpolation can erase which pom owned one. Traversal still follows Maven's
 * declaration structure, so coordinate-shaped XML belonging to a plugin's arbitrary configuration is
 * not mistaken for a project dependency or plugin.
 */
@UtilityClass
public final class DeclaredCoordinates {

    private static final String MAVEN_PLUGIN_GROUP = "org.apache.maven.plugins";
    private static final String DEPENDENCIES = "dependencies";
    private static final String PROPERTY_PREFIX = "${";
    private static final String PROPERTY_SUFFIX = "}";

    /**
     * Every versioned dependency and plugin declaration in a pom.
     *
     * @param pom pom to read
     * @return distinct coordinates in document order
     */
    public static List<DeclaredCoordinate> from(Path pom) {
        Element root = Xml.parse(read(pom)).getDocumentElement();
        Stream<DeclaredCoordinate> dependencies = Stream.concat(
            dependencies(root), paths(root)
        ).map(node -> coordinate(node, properties(root, node), "")).flatMap(Optional::stream);
        Stream<DeclaredCoordinate> plugins = plugins(root)
            .map(node -> coordinate(node, properties(root, node), MAVEN_PLUGIN_GROUP))
            .flatMap(Optional::stream);
        Stream<DeclaredCoordinate> parents = Xml.firstChild(root, "parent").stream()
            .map(node -> coordinate(node, properties(root, node), ""))
            .flatMap(Optional::stream);
        return Stream.of(dependencies, plugins, parents).flatMap(stream -> stream).distinct().toList();
    }

    /**
     * The parent the pom declares, if it has one.
     *
     * @param pom pom to read
     * @return the declared parent with its locally owned property resolved
     */
    public static Optional<DeclaredCoordinate> parent(Path pom) {
        Element root = Xml.parse(read(pom)).getDocumentElement();
        return Xml.firstChild(root, "parent")
            .flatMap(node -> coordinate(node, properties(root, node), ""));
    }

    private static Optional<DeclaredCoordinate> coordinate(
        Node element, Map<String, String> properties, String defaultGroup
    ) {
        Optional<String> group = Xml.text(element, "groupId").or(() -> Optional.of(defaultGroup));
        Optional<String> artifact = Xml.text(element, "artifactId");
        Optional<String> version = Xml.text(element, "version").map(raw -> resolve(raw, properties));
        return group.filter(held -> !held.isEmpty()).flatMap(
            heldGroup -> artifact.flatMap(
                heldArtifact -> version.map(
                    heldVersion -> new DeclaredCoordinate(heldGroup, heldArtifact, heldVersion)
                )
            )
        );
    }

    private static String resolve(String raw, Map<String, String> properties) {
        if (raw.startsWith(PROPERTY_PREFIX) && raw.endsWith(PROPERTY_SUFFIX)) {
            String key = raw.substring(PROPERTY_PREFIX.length(), raw.length() - PROPERTY_SUFFIX.length());
            return properties.getOrDefault(key, raw);
        }
        return raw;
    }

    private static Map<String, String> properties(Node root, Node declaration) {
        Stream<Node> project = Xml.firstChild(root, "properties").stream().map(Node.class::cast);
        Stream<Node> profile = ancestors(declaration)
            .filter(node -> named(node, "profile"))
            .findFirst()
            .stream()
            .flatMap(node -> Xml.firstChild(node, "properties").stream())
            .map(Node.class::cast);
        return Stream.concat(project, profile)
            .flatMap(DeclaredCoordinates::entries)
            .collect(
                Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (_, second) -> second)
            );
    }

    private static Stream<Map.Entry<String, String>> entries(Node properties) {
        NodeList nodes = properties.getChildNodes();
        return IntStream.range(0, nodes.getLength())
            .mapToObj(nodes::item)
            .filter(node -> node.getNodeType() == Node.ELEMENT_NODE)
            .map(Element.class::cast)
            .map(element -> Map.entry(element.getTagName(), element.getTextContent().strip()));
    }

    static Stream<Node> dependencies(Element root) {
        return elements(root, "dependency").filter(DeclaredCoordinates::isDependency);
    }

    static Stream<Node> plugins(Element root) {
        return elements(root, "plugin").filter(PluginDeclaration::matches);
    }

    static Stream<Node> paths(Element root) {
        return elements(root, "path").filter(DeclaredCoordinates::isAnnotationProcessorPath);
    }

    static Stream<Node> propertyBlocks(Element root) {
        return elements(root, "properties")
            .filter(node -> projectOrProfile(node.getParentNode()));
    }

    private static Stream<Node> elements(Element root, String tag) {
        NodeList nodes = root.getElementsByTagName(tag);
        return IntStream.range(0, nodes.getLength()).mapToObj(nodes::item);
    }

    private static boolean isDependency(Node node) {
        Node dependencies = node.getParentNode();
        if (!named(dependencies, DEPENDENCIES)) {
            return false;
        }
        Node owner = dependencies.getParentNode();
        return projectOrProfile(owner)
            || isManagedDependency(owner)
            || isPluginDependency(owner);
    }

    private static boolean isManagedDependency(Node owner) {
        return named(owner, "dependencyManagement") && projectOrProfile(owner.getParentNode());
    }

    private static boolean isPluginDependency(Node owner) {
        return named(owner, "plugin") && PluginDeclaration.matches(owner);
    }

    private static boolean isAnnotationProcessorPath(Node node) {
        Node paths = node.getParentNode();
        Optional<Node> configuration = Optional.ofNullable(paths).map(Node::getParentNode);
        Optional<Node> plugin = configuration.stream()
            .flatMap(DeclaredCoordinates::ancestors)
            .filter(ancestor -> named(ancestor, "plugin"))
            .findFirst();
        return named(paths, "annotationProcessorPaths")
            && configuration.filter(held -> named(held, "configuration")).isPresent()
            && plugin.filter(PluginDeclaration::matches).isPresent();
    }

    private static boolean projectOrProfile(Node node) {
        return named(node, "project") || named(node, "profile");
    }

    private static Stream<Node> ancestors(Node node) {
        return Stream.iterate(node.getParentNode(), Objects::nonNull, Node::getParentNode);
    }

    private static boolean named(Node node, String name) {
        return Optional.ofNullable(node)
            .filter(held -> held.getNodeType() == Node.ELEMENT_NODE)
            .map(Node::getNodeName)
            .filter(name::equals)
            .isPresent();
    }

    private static String read(Path pom) {
        try {
            return Files.readString(pom);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + pom, exception);
        }
    }

    @UtilityClass
    private static final class PluginDeclaration {

        static boolean matches(Node node) {
            Node plugins = node.getParentNode();
            Node owner = plugins.getParentNode();
            return named(plugins, "plugins") && (direct(owner) || managed(owner));
        }

        private static boolean direct(Node owner) {
            return (named(owner, "build") || named(owner, "reporting"))
                && projectOrProfile(owner.getParentNode());
        }

        private static boolean managed(Node owner) {
            return named(owner, "pluginManagement")
                && named(owner.getParentNode(), "build")
                && projectOrProfile(owner.getParentNode().getParentNode());
        }
    }
}
