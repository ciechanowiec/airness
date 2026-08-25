package eu.ciechanowiec.airness.governance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Reading the project files of this repository the way Maven reads them.
 *
 * <p>Separate from {@link SelfHarnessMirrorTest} because navigating a project file and comparing two of
 * them are different jobs, and holding both in one class made it a class with more methods than the rule
 * set allows. The navigation is the half a later self-test will want as well.
 */
@UtilityClass
final class ProjectFiles {

    private static final String PARENT = "airness-parent";
    private static final String MAIN = "src/main/java";
    private static final String POM = "pom.xml";

    /**
     * The project file of the root aggregator.
     *
     * @return its path
     */
    static Path rootPom() {
        return SelfModules.repository().resolve(POM);
    }

    /**
     * The project file consumers inherit.
     *
     * @return its path
     */
    static Path parentPom() {
        return SelfModules.repository().resolve(PARENT).resolve(POM);
    }

    /**
     * The names of the declared modules, in the order the root project file declares them.
     *
     * @return the module names
     */
    static List<String> modules() {
        Element declaration = child(document(rootPom()), "modules");
        return Xml.children(declaration, "module").stream()
            .map(Element::getTextContent)
            .map(String::strip)
            .toList();
    }

    /**
     * The project file of every declared module other than the consumer-facing parent.
     *
     * @return their paths
     */
    static List<Path> moduleFiles() {
        Path root = SelfModules.repository();
        return modules().stream()
            .filter(module -> !PARENT.equals(module))
            .map(module -> root.resolve(module).resolve(POM))
            .toList();
    }

    /**
     * The project file of every module that holds production Java.
     *
     * @return their paths
     */
    static List<Path> withProductionJava() {
        return moduleFiles().stream().filter(pom -> Files.isDirectory(pom.resolveSibling(MAIN))).toList();
    }

    /**
     * The directory name of the module a project file belongs to.
     *
     * @param pom a module project file
     * @return the module name
     */
    static String moduleName(Path pom) {
        return pom.resolveSibling("").getFileName().toString();
    }

    /**
     * The managed declaration of one plugin.
     *
     * @param pom      a project file
     * @param artifact the plugin artifact identifier
     * @return the plugin element under pluginManagement
     */
    static Element managed(Path pom, String artifact) {
        Element plugins = child(document(pom), "build", "pluginManagement", "plugins");
        return Xml.children(plugins, "plugin").stream()
            .filter(plugin -> artifact.equals(Xml.text(plugin, "artifactId").orElse("")))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(pom + " manages no " + artifact));
    }

    /**
     * The bound declaration of one plugin, read from the build rather than from a profile.
     *
     * @param pom      a project file
     * @param artifact the plugin artifact identifier
     * @return the plugin element under build/plugins
     */
    static Element bound(Path pom, String artifact) {
        Element plugins = child(document(pom), "build", "plugins");
        return Xml.children(plugins, "plugin").stream()
            .filter(plugin -> artifact.equals(Xml.text(plugin, "artifactId").orElse("")))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(pom + " binds no " + artifact));
    }

    /**
     * The declared value of one property of a project file.
     *
     * @param pom  a project file
     * @param name the property name
     * @return the value as written, stripped
     */
    static String property(Path pom, String name) {
        return Xml.text(child(document(pom), "properties"), name)
            .map(String::strip)
            .orElseThrow(() -> new IllegalStateException(pom + " declares no " + name));
    }

    /**
     * One bound execution, wherever in the file it is written.
     *
     * @param pom a project file
     * @param id  the execution identifier
     * @return the execution element
     */
    static Element execution(Path pom, String id) {
        return descendants(document(pom), "execution")
            .filter(execution -> id.equals(Xml.text(execution, "id").orElse("")))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(pom + " binds no execution " + id));
    }

    /**
     * The element reached by following a path of tag names down from one node.
     *
     * @param from the node to start at
     * @param path the tag names to follow, outermost first
     * @return the element the path names
     */
    static Element child(Element from, String... path) {
        return Stream.of(path)
            .reduce(
                Optional.of(from),
                (node, tag) -> node.flatMap(parent -> Xml.firstChild(parent, tag)),
                (_, second) -> second
            )
            .orElseThrow(() -> new IllegalStateException("no " + String.join("/", path) + " under " + from));
    }

    /**
     * Every element of one tag name anywhere below a node.
     *
     * @param root the node to read below
     * @param tag  the tag name to collect
     * @return the matching elements
     */
    static Stream<Element> descendants(Node root, String tag) {
        return Stream.concat(
            Xml.children(root, tag).stream(),
            elementChildren(root).flatMap(node -> descendants(node, tag))
        ).distinct();
    }

    /**
     * Every element child of one node. {@code Xml.children} matches a tag name literally, so it cannot be
     * asked for all of them: the star it would be asked with is a name no element has.
     *
     * @param parent the node to read
     * @return its element children
     */
    static Stream<Element> elementChildren(Node parent) {
        NodeList nodes = parent.getChildNodes();
        return IntStream.range(0, nodes.getLength())
            .mapToObj(nodes::item)
            .filter(node -> node.getNodeType() == Node.ELEMENT_NODE)
            .map(Element.class::cast);
    }

    /**
     * The root element of one project file.
     *
     * @param pom the file to read
     * @return its document element
     */
    @SneakyThrows
    static Element document(Path pom) {
        return Xml.parse(Files.readString(pom)).getDocumentElement();
    }
}
