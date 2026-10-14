package eu.ciechanowiec.airness.errorprone;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Holds the compiler-side rules that carry the modernization requirements to their examples.
 *
 * <p>A compiler-side rule written for a requirement reports every violation and none of its lookalikes. A
 * reused one reports every violation. A requirement the compiler already enforces through deprecation
 * warnings, which Airness turns into errors, is shown failing to compile under those same flags.
 */
class ModernizationCasesTest {

    @Test
    void errorProneRulesReportTheViolationsAndOnlyThem(@TempDir Path directory) {
        List<Case> cases = cases("ErrorProne");
        assertFalse(cases.isEmpty(), "the Error Prone rules have examples");
        assertAll(
            IntStream.range(0, cases.size()).mapToObj(
                index -> () -> cases.get(index).assertVerdict(directory.resolve(String.valueOf(index)))
            )
        );
    }

    @Test
    void deprecationsAreRefusedByTheCompiler(@TempDir Path directory) {
        List<Case> cases = cases("Javac");
        assertFalse(cases.isEmpty(), "the compiler-enforced requirements have examples");
        assertAll(
            IntStream.range(0, cases.size()).mapToObj(
                index -> () -> assertTrue(
                    JavacRule.refusesDeprecation(directory.resolve(String.valueOf(index)), cases.get(index).source()),
                    () -> cases.get(index).name() + " is refused for its deprecated use"
                )
            )
        );
    }

    private static List<Case> cases(String tool) {
        Document inventory = inventory();
        NodeList rules = inventory.getElementsByTagName("rule");
        return IntStream.range(0, rules.getLength())
            .mapToObj(index -> (Element) rules.item(index))
            .filter(rule -> tool.equals(rule.getAttribute("tool")))
            .flatMap(rule -> children(rule, "case").stream().map(sample -> Case.read(rule, sample)))
            .toList();
    }

    private static List<Element> children(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return IntStream.range(0, nodes.getLength()).mapToObj(index -> (Element) nodes.item(index)).toList();
    }

    private static Document inventory() {
        try (
            InputStream stream = Optional.ofNullable(
                ModernizationCasesTest.class.getResource("/modernization-cases.xml")
            ).orElseThrow().openStream()
        ) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(stream);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        } catch (ParserConfigurationException | SAXException exception) {
            throw new IllegalStateException("The modernization cases do not parse", exception);
        }
    }

    private record Case(String check, String name, boolean flagged, String source, String scope) {

        static Case read(Element rule, Element sample) {
            return new Case(
                rule.getAttribute("id"),
                sample.getAttribute("name"),
                Boolean.parseBoolean(sample.getAttribute("flagged")),
                children(sample, "source").getFirst().getTextContent(),
                sample.getAttribute("scope")
            );
        }

        void assertVerdict(Path directory) {
            String description = this.check + " over " + this.name;
            Path root = assertDoesNotThrow(() -> Files.createDirectories(directory), description);
            boolean reported = !assertDoesNotThrow(
                () -> ErrorProneRule.findingsIn(root, this.check, this.scope, this.source), description
            ).isEmpty();
            if (this.flagged) {
                assertTrue(reported, () -> description + " reports the violation");
            } else {
                assertFalse(reported, () -> description + " accepts the lookalike");
            }
        }
    }
}
