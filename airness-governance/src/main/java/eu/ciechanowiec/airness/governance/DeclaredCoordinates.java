package eu.ciechanowiec.airness.governance;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
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
 * coordinate, and model interpolation can erase which pom owned one.
 */
@UtilityClass
public final class DeclaredCoordinates {

    private static final String MAVEN_PLUGIN_GROUP = "org.apache.maven.plugins";
    private static final String PROPERTY_PREFIX = "${";
    private static final String PROPERTY_SUFFIX = "}";

    /**
     * Every versioned dependency and plugin declaration in a pom.
     *
     * @param pom pom to read
     * @return distinct coordinates in document order
     */
    public static List<DeclaredCoordinate> from(Path pom) {
        Node root = Xml.parse(read(pom)).getDocumentElement();
        Map<String, String> properties = properties(root);
        Stream<DeclaredCoordinate> dependencies = Stream.concat(
            elements(root, "dependency"), elements(root, "path")
        ).map(node -> coordinate(node, properties, "")).flatMap(Optional::stream);
        Stream<DeclaredCoordinate> plugins = elements(root, "plugin")
            .map(node -> coordinate(node, properties, MAVEN_PLUGIN_GROUP))
            .flatMap(Optional::stream);
        Stream<DeclaredCoordinate> parents = Xml.firstChild(root, "parent").stream()
            .map(node -> coordinate(node, properties, ""))
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
        Node root = Xml.parse(read(pom)).getDocumentElement();
        Map<String, String> properties = properties(root);
        return Xml.firstChild(root, "parent").flatMap(node -> coordinate(node, properties, ""));
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

    private static Map<String, String> properties(Node root) {
        return elements(root, "properties")
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

    private static Stream<Node> elements(Node root, String tag) {
        NodeList nodes = ((Element) root).getElementsByTagName(tag);
        return IntStream.range(0, nodes.getLength()).mapToObj(nodes::item);
    }

    private static String read(Path pom) {
        try {
            return Files.readString(pom);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read " + pom, exception);
        }
    }
}
