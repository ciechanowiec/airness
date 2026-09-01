package eu.ciechanowiec.airness.maven;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import org.w3c.dom.NodeList;

/**
 * The modules of this repository that carry Java, read from the root project file rather than written
 * out.
 *
 * <p>The reactor is the right source and the filesystem is not. {@code airness-it} holds production
 * Java too, and every source in it breaks a rule on purpose, so a scan that walked directories would
 * report the fixtures as findings. The root project file names exactly the modules the harness is held
 * to, which is the same set Maven builds.
 *
 * <p>There is a class of this name in the test sources of {@code airness-governance} as well. Test
 * sources are not published, so sharing one would mean publishing a test artifact for two callers, and
 * that is a heavier thing to own than twenty lines that each module can read on its own.
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
     * The source roots of every declared module that holds production Java.
     *
     * @return the production and test source roots that exist under those modules
     */
    static List<Path> sourceRoots() {
        Path root = repository();
        return declared().stream()
            .map(root::resolve)
            .filter(module -> Files.isDirectory(module.resolve(MAIN)))
            .flatMap(module -> Stream.of(MAIN, TEST).map(module::resolve))
            .filter(Files::isDirectory)
            .toList();
    }

    /**
     * Every declared module directory that holds production Java.
     *
     * @return module directories in reactor order
     */
    static List<Path> withProductionJava() {
        Path root = repository();
        return declared().stream()
            .map(root::resolve)
            .filter(module -> Files.isDirectory(module.resolve(MAIN)))
            .toList();
    }

    @SneakyThrows
    private static List<String> declared() {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setExpandEntityReferences(false);
        byte[] content = Files.readString(repository().resolve("pom.xml")).getBytes(StandardCharsets.UTF_8);
        NodeList modules = factory.newDocumentBuilder()
            .parse(new ByteArrayInputStream(content))
            .getElementsByTagName("module");
        List<String> names = IntStream.range(0, modules.getLength())
            .mapToObj(modules::item)
            .map(node -> node.getTextContent().strip())
            .toList();
        if (names.isEmpty()) {
            throw new IllegalStateException("the root project file declares no modules");
        }
        return names;
    }
}
