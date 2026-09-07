package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.ciechanowiec.airness.governance.CheckstyleConfigurationTest.Fixture;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The source parser refuses unreadable candidates and extracts only explicit string references.
 */
class SpringViewNamesTest {

    @Test
    void avoidsParsingASourceThatCannotContainViewReferences() {
        assertEquals(List.of(), names("class Plain { String text() { return \"value\"; } }"));
    }

    @Test
    void refusesAnUnreadableControllerInsteadOfReportingItClean() {
        IllegalArgumentException failure = assertThrows(
            IllegalArgumentException.class, () -> names("@Controller class Page { @GetMapping String page( }")
        );
        assertTrue(failure.toString().contains("Page.java: cannot read view references: [line 1:"));
    }

    @Test
    void leavesVoidReturnsAndNonStringLiteralsAlone() {
        String source = """
            @Controller class Page {
                @GetMapping void empty() { return; }
                @GetMapping int number() { return 1; }
                @GetMapping Object absent() { return null; }
                @GetMapping String dynamic() { return view(); }
                @GetMapping String parameter(String value) { return value; }
            }
            """;
        assertEquals(List.of(), names(source));
    }

    @Test
    void readsAnExplicitModelOutsideAController() {
        String source = "class Page { Object model() { return new ModelAndView(\"room/list\"); } }";
        List<SpringViewNames.Reference> references = names(source);
        assertEquals(1, references.size());
        assertEquals("\"room/list\"", references.getFirst().written());
        assertEquals(source.indexOf("new ModelAndView"), references.getFirst().offset());
    }

    @Test
    void neverReadsQuotedJavaAsAConstructionOrReturn() {
        String source = """
            @Controller class Page {
                String marker = "return GONE; new ModelAndView(GONE)";
                @GetMapping String page() { return "room/list"; }
            }
            """;
        List<SpringViewNames.Reference> references = names(source);
        assertEquals(1, references.size());
        assertEquals("\"room/list\"", references.getFirst().written());
    }

    @Test
    void permitsTheJdkTraversalExtensionPoint(@TempDir Path directory) {
        Fixture fixture = new Fixture(
            "Sample.java", "final class Sample extends TreeScanner<Void, Void> {}", "Inheritance is allowed", 1
        );
        assertTrue(
            CheckstyleConfigurationTest.inspect(directory, fixture, false).stream()
                .noneMatch(finding -> finding.matches(fixture)),
            "the JDK visitor is extended through its declared hooks"
        );
    }

    @Test
    void stillRefusesAnUnrelatedTraversalBaseClass(@TempDir Path directory) {
        Fixture fixture = new Fixture(
            "Sample.java", "final class Sample extends %s<Void, Void> {}".formatted("CustomTreeScanner"),
            "Inheritance is allowed", 1
        );
        assertTrue(
            CheckstyleConfigurationTest.inspect(directory, fixture, false).stream()
                .anyMatch(finding -> finding.matches(fixture)),
            "the exception names the exact JDK base class"
        );
    }

    private static List<SpringViewNames.Reference> names(String source) {
        Path path = Path.of("src/main/java/sample/Page.java");
        return SpringViewNames.in(new SpringTypes.Declared(path, "Page", JavaCode.blanked(source), source));
    }
}
