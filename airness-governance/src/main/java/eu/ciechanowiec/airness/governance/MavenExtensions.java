package eu.ciechanowiec.airness.governance;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;
import lombok.experimental.UtilityClass;
import org.w3c.dom.NodeList;

/**
 * Reads the build extensions a project loads: the {@code extension} elements of
 * {@code .mvn/extensions.xml} and of a pom's {@code build}.
 *
 * <p>An extension is not a dependency and not a plugin, so {@link DeclaredCoordinates} does not reach
 * it, and the licence allowlist never sees it either. It is also the one place a build service such as
 * Develocity is wired in, which is why the blocklist reads it. A pom's extension may omit its version
 * where the pom manages it, and the empty version that leaves is one no floor can place, so an entry
 * with a floor refuses it and an entry without one refuses it anyway.
 */
@UtilityClass
final class MavenExtensions {

    private static final String EXTENSION = "extension";

    /**
     * Every extension a file declares.
     *
     * @param file the extensions file or the pom
     * @return the coordinates, or none when the file is absent or declares none
     */
    static List<DeclaredCoordinate> in(Path file) {
        return Repository.readText(file).map(MavenExtensions::declared).orElse(List.of());
    }

    private static List<DeclaredCoordinate> declared(String xml) {
        NodeList nodes = Xml.parse(xml).getDocumentElement().getElementsByTagName(EXTENSION);
        return IntStream.range(0, nodes.getLength())
            .mapToObj(nodes::item)
            .map(
                node -> new DeclaredCoordinate(
                    Xml.text(node, "groupId").orElse(""),
                    Xml.text(node, "artifactId").orElse(""),
                    Xml.text(node, "version").orElse("")
                )
            )
            .filter(coordinate -> !coordinate.artifactId().isEmpty())
            .toList();
    }
}
