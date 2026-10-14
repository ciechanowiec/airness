package eu.ciechanowiec.airness.errorprone;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Holds each rule to the boundaries the shared examples do not reach: other receivers, other overloads,
 * shadowed names, other scopes and the forms the rule deliberately leaves alone.
 */
class ModernizationEdgesTest {

    @Test
    void holdsEachRuleToItsBoundaries(@TempDir Path directory) {
        List<Edge> edges = edges();
        assertFalse(edges.isEmpty(), "the boundaries have examples");
        assertAll(
            IntStream.range(0, edges.size()).mapToObj(
                index -> () -> edges.get(index).assertFindings(directory.resolve(String.valueOf(index)))
            )
        );
    }

    private static List<Edge> edges() {
        try (
            InputStream stream = Optional.ofNullable(
                ModernizationEdgesTest.class.getResource("/modernization-edges.xml")
            ).orElseThrow().openStream()
        ) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            NodeList nodes = factory.newDocumentBuilder().parse(stream).getElementsByTagName("edge");
            return IntStream.range(0, nodes.getLength())
                .mapToObj(index -> (Element) nodes.item(index))
                .map(Edge::read)
                .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (ParserConfigurationException | SAXException exception) {
            throw new IllegalStateException("The modernization edges do not parse", exception);
        }
    }

    private record Edge(String check, int findings, String body) {

        static Edge read(Element edge) {
            return new Edge(
                edge.getAttribute("check"), Integer.parseInt(edge.getAttribute("findings")), edge.getTextContent()
            );
        }

        void assertFindings(Path directory) {
            String source = "package example;\n\n" + this.body + "\n";
            assertEquals(
                this.findings, ErrorProneRule.findings(directory, this.check, source).size(),
                () -> this.check + " over " + this.body
            );
        }
    }
}
