package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

class ManagedVersionsPolicyTest {

    @Test
    void policyClassifiesEveryRootAndParentCoordinate() {
        Set<String> expected = ManagedVersions.coordinates().stream()
            .map(ManagedVersions.Coordinate::key)
            .collect(Collectors.toUnmodifiableSet());
        Set<String> actual = sourcePoms().flatMap(ManagedVersionsPolicyTest::coordinateKeys)
            .collect(Collectors.toUnmodifiableSet());
        assertEquals(expected, actual);
    }

    @Test
    void rootManagementPinsEveryPolicyCoordinate() {
        Element root = parse(repository().resolve("pom.xml"));
        assertAll(
            ManagedVersions.coordinates().stream()
                .map(coordinate -> () -> assertManaged(root, coordinate))
        );
    }

    @Test
    void everyVersionPropertyIsOwnedOnceAtTheRoot() {
        Element root = parse(repository().resolve("pom.xml"));
        Element parent = parse(repository().resolve("airness-parent/pom.xml"));
        Set<String> rootProperties = governedProperties(root);
        assertEquals(ManagedVersions.protectedProperties(), rootProperties);
        assertTrue(governedProperties(parent).isEmpty());
        Element mavenRule = elements(root, "requireMavenVersion").findFirst().orElseThrow();
        Element javaRule = elements(root, "requireJavaVersion").findFirst().orElseThrow();
        assertAll(
            () -> assertEquals("[3.9.16,)", Xml.text(mavenRule, "version").orElse("")),
            () -> assertEquals("[25,26)", Xml.text(javaRule, "version").orElse(""))
        );
    }

    @Test
    void pluginDeclarationsOutsideManagementCarryNoVersion() {
        List<String> versioned = sourcePoms()
            .flatMap(root -> elements(root, "plugin"))
            .filter(plugin -> !hasAncestor(plugin, "pluginManagement"))
            .filter(plugin -> Xml.firstChild(plugin, "version").isPresent())
            .map(ManagedVersionsPolicyTest::coordinate)
            .toList();
        assertEquals(List.of(), versioned);
    }

    @Test
    void projectDependenciesOutsideManagementCarryNoVersion() {
        List<String> versioned = sourcePoms()
            .flatMap(root -> Xml.firstChild(root, "dependencies").stream())
            .flatMap(dependencies -> Xml.children(dependencies, "dependency").stream())
            .filter(dependency -> Xml.firstChild(dependency, "version").isPresent())
            .map(ManagedVersionsPolicyTest::coordinate)
            .toList();
        assertEquals(List.of(), versioned);
    }

    @Test
    void isolatedClasspathEntriesUseTheOwnedPropertyExplicitly() {
        assertAll(
            sourcePoms()
                .flatMap(ManagedVersionsPolicyTest::isolatedClasspathEntries)
                .map(declaration -> () -> assertExplicitOwnedVersion(declaration))
        );
    }

    private static void assertManaged(Element root, ManagedVersions.Coordinate coordinate) {
        String management = coordinate.kind() == ManagedVersions.Kind.PLUGIN
            ? "pluginManagement"
            : "dependencyManagement";
        String declaration = coordinate.kind() == ManagedVersions.Kind.PLUGIN ? "plugin" : "dependency";
        String expected = "${" + coordinate.property() + '}';
        boolean pinned = elements(root, declaration)
            .filter(element -> hasAncestor(element, management))
            .filter(coordinate::matches)
            .map(element -> Xml.text(element, "version").orElse(""))
            .anyMatch(expected::equals);
        assertTrue(pinned, coordinate.key());
    }

    private static void assertExplicitOwnedVersion(Node declaration) {
        ManagedVersions.Coordinate coordinate = ManagedVersions.coordinates().stream()
            .filter(candidate -> candidate.kind() == ManagedVersions.Kind.DEPENDENCY)
            .filter(candidate -> candidate.matches(declaration))
            .findFirst()
            .orElseThrow();
        String expected = "${" + coordinate.property() + '}';
        assertEquals(expected, Xml.text(declaration, "version").orElse(""), coordinate.key());
    }

    private static Stream<Element> isolatedClasspathEntries(Element root) {
        Stream<Element> pluginDependencies = elements(root, "dependency")
            .filter(dependency -> hasAncestor(dependency, "plugin"));
        return Stream.concat(pluginDependencies, elements(root, "path"));
    }

    private static Stream<String> coordinateKeys(Element root) {
        Stream<String> plugins = elements(root, "plugin")
            .map(element -> key(ManagedVersions.Kind.PLUGIN, element));
        Stream<String> dependencies = Stream.concat(
            elements(root, "dependency"),
            elements(root, "path")
        ).map(element -> key(ManagedVersions.Kind.DEPENDENCY, element));
        return Stream.concat(plugins, dependencies);
    }

    private static String key(ManagedVersions.Kind kind, Node node) {
        String defaultGroup = kind == ManagedVersions.Kind.PLUGIN
            ? "org.apache.maven.plugins"
            : "";
        String group = Xml.text(node, "groupId").orElse(defaultGroup);
        String artifact = Xml.text(node, "artifactId").orElse("");
        return kind + ":" + group + ':' + artifact;
    }

    private static Set<String> governedProperties(Node root) {
        return Xml.firstChild(root, "properties").stream()
            .flatMap(ManagedVersionsPolicyTest::directElements)
            .map(Element::getTagName)
            .filter(ManagedVersions.protectedProperties()::contains)
            .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean hasAncestor(Node node, String tag) {
        return Stream.iterate(node.getParentNode(), Objects::nonNull, Node::getParentNode)
            .filter(parent -> parent.getNodeType() == Node.ELEMENT_NODE)
            .map(Element.class::cast)
            .anyMatch(element -> element.getTagName().equals(tag));
    }

    private static String coordinate(Node node) {
        return Xml.text(node, "groupId").orElse("org.apache.maven.plugins") + ':'
            + Xml.text(node, "artifactId").orElse("");
    }

    private static Stream<Element> sourcePoms() {
        Path repository = repository();
        return Stream.of(repository.resolve("pom.xml"), repository.resolve("airness-parent/pom.xml"))
            .map(ManagedVersionsPolicyTest::parse);
    }

    private static Stream<Element> elements(Element root, String tag) {
        return IntStream.range(0, root.getElementsByTagName(tag).getLength())
            .mapToObj(index -> root.getElementsByTagName(tag).item(index))
            .map(Element.class::cast);
    }

    private static Stream<Element> directElements(Node root) {
        return IntStream.range(0, root.getChildNodes().getLength())
            .mapToObj(index -> root.getChildNodes().item(index))
            .filter(node -> node.getNodeType() == Node.ELEMENT_NODE)
            .map(Element.class::cast);
    }

    private static Element parse(Path pom) {
        return Xml.parse(read(pom)).getDocumentElement();
    }

    @SneakyThrows
    private static String read(Path path) {
        return Files.readString(path);
    }

    private static Path repository() {
        Path current = Path.of("").toAbsolutePath();
        return Stream.of(current, current.getParent())
            .filter(path -> Files.exists(path.resolve("airness-parent/pom.xml")))
            .findFirst()
            .orElseThrow();
    }
}
