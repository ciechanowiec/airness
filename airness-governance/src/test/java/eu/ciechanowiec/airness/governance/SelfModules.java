package eu.ciechanowiec.airness.governance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.w3c.dom.Element;

/**
 * The modules of this repository that carry Java, read from the root project file rather than written
 * out.
 *
 * <p>A list written out is a list that goes stale silently. A module added to the reactor without its
 * name being added here as well would escape every self-test that reads this, and escape it by passing:
 * the tests would go on reporting a clean verdict over the modules they still knew about. Reading the
 * modules from the same place Maven reads them means a new module is scanned the moment it exists.
 */
@UtilityClass
final class SelfModules {

    private static final String MAIN = "src/main/java";
    private static final String TEST = "src/test/java";

    /**
     * The repository root, found by the project file only it carries.
     *
     * @return the absolute path of the repository root
     */
    static Path repository() {
        Path current = Path.of("").toAbsolutePath();
        return Stream.of(current, current.getParent())
            .filter(path -> Files.exists(path.resolve("airness-parent/pom.xml")))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("no repository root above " + current));
    }

    /**
     * Every declared module directory that holds production Java.
     *
     * @return the module directories, in the order the root project file declares them
     */
    static List<Path> withProductionJava() {
        Path root = repository();
        return declared().stream()
            .map(root::resolve)
            .filter(module -> Files.isDirectory(module.resolve(MAIN)))
            .toList();
    }

    /**
     * The source roots of one module that exist.
     *
     * @param module a module directory
     * @return the production and test source roots present under it
     */
    static List<Path> sourceRoots(Path module) {
        return Stream.of(MAIN, TEST)
            .map(module::resolve)
            .filter(Files::isDirectory)
            .toList();
    }

    /**
     * The test source root of one module.
     *
     * @param module a module directory
     * @return the test source root, or nothing when the module has none
     */
    static List<Path> testRoots(Path module) {
        return Stream.of(module.resolve(TEST)).filter(Files::isDirectory).toList();
    }

    @SneakyThrows
    private static List<String> declared() {
        Element project = Xml.parse(Files.readString(repository().resolve("pom.xml"))).getDocumentElement();
        List<Element> modules = Xml.firstChild(project, "modules")
            .map(declaration -> Xml.children(declaration, "module"))
            .orElseThrow(() -> new IllegalStateException("the root project file declares no modules"));
        return modules.stream().map(Element::getTextContent).map(String::strip).toList();
    }
}
