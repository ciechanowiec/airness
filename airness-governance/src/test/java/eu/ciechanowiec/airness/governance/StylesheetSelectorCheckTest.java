package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class StylesheetSelectorCheckTest {

    private static final List<Path> RESOURCES = List.of(Path.of("src", "main", "resources"));

    private static final String SHEET = "src/main/resources/static/css/theme.css";

    private static final String BROKEN = """
        a:not(.button):not(.rail-item) :not(.crumb):not(.tile-name) {
            color: var(--color-action);
        }
        """;

    private static final String WHOLE = """
        a:not(.button, .rail-item, .crumb, .tile-name) {
            color: var(--color-action);
        }
        """;

    @Test
    void reportsAChainOfRefusalsWithASpaceInTheMiddleOfIt() {
        Path root = new GitFixture("selectors-broken").write(SHEET, BROKEN).root();
        assertEquals(1, offences(root).size(), "a space there selects inside a link rather than the link");
    }

    @Test
    void reportsTheSelectorUnderThePlaceItWasWritten() {
        Path root = new GitFixture("selectors-located").write(SHEET, BROKEN).root();
        assertTrue(
            offences(root).getFirst().startsWith("src/main/resources/static/css/theme.css:1:"),
            "an offence names the file and the line the selector is on"
        );
    }

    @Test
    void saysWhatIsWrongAndWhatToDoAboutIt() {
        Path root = new GitFixture("selectors-worded").write(SHEET, BROKEN).root();
        String offence = offences(root).getFirst();
        assertTrue(offence.contains("a space joins two refusals"), "an offence says what the space did");
        assertTrue(offence.contains("name the parts in one refusal"), "and says how to repair it");
    }

    @Test
    void leavesASelectorNamingEveryRefusalAtOnceAlone() {
        Path root = new GitFixture("selectors-whole").write(SHEET, WHOLE).root();
        assertTrue(Verdicts.clean(findings(root)), "a selector written on one line means what it names");
    }

    @Test
    void leavesAGroupWrittenInsideSomethingAlone() {
        String grouped = """
            .table :is(th, td) {
                padding: 0.5rem;
            }

            .field :where(.input, .select) {
                width: 100%;
            }
            """;
        Path root = new GitFixture("selectors-grouped").write(SHEET, grouped).root();
        assertTrue(Verdicts.clean(findings(root)), "a table naming its own cells means exactly what it writes");
    }

    @Test
    void leavesARefusalAboutWhatSomethingHoldsAlone() {
        String inside = """
            .card :not(.hidden) {
                opacity: 1;
            }
            """;
        Path root = new GitFixture("selectors-inside").write(SHEET, inside).root();
        assertTrue(
            Verdicts.clean(findings(root)),
            "refusing one thing about what a card holds is an ordinary selector somebody meant"
        );
    }

    @Test
    void readsARefusalCarryingAGroupInsideIt() {
        String nested = """
            a:not(:where(.button, .tab)) :not(.crumb) {
                text-decoration: underline;
            }
            """;
        Path root = new GitFixture("selectors-nested").write(SHEET, nested).root();
        assertEquals(1, offences(root).size(), "a refusal holding a group is still the half before the space");
    }

    @Test
    void passesOverTheConstructNamedInAComment() {
        String commented = """
            /* A chain such as :not(.button) :not(.crumb) is what this file must never write. */
            a:not(.button, .crumb) {
                color: inherit;
            }
            """;
        Path root = new GitFixture("selectors-commented").write(SHEET, commented).root();
        assertTrue(Verdicts.clean(findings(root)), "prose naming the mistake is not the file making it");
    }

    @Test
    void passesOverTheConstructWrittenInsideQuotedText() {
        String quoted = """
            .note::before {
                content: ":not(.a) :not(.b)";
            }
            """;
        Path root = new GitFixture("selectors-quoted").write(SHEET, quoted).root();
        assertTrue(Verdicts.clean(findings(root)), "text a page prints is not a selector the page is drawn by");
    }

    @Test
    void readsEveryBrokenSelectorInOneFile() {
        String several = BROKEN + "\n" + BROKEN.replace("a:not", "button:not");
        Path root = new GitFixture("selectors-several").write(SHEET, several).root();
        assertEquals(2, offences(root).size(), "a file is read whole rather than to its first offence");
    }

    @Test
    void readsNothingThatSomebodyElsePublished() {
        Path root = new GitFixture("selectors-vendored")
            .write("src/main/resources/static/vendor/published.css", BROKEN).root();
        assertTrue(
            Verdicts.clean(findings(root)),
            "a stylesheet a project may not edit is not a stylesheet it can be held to"
        );
    }

    @Test
    void readsNothingThatSitsOutsideTheResourcesOfTheModule() {
        Path root = new GitFixture("selectors-outside").write("docs/theme.css", BROKEN).root();
        assertTrue(Verdicts.clean(findings(root)), "a stylesheet outside the module's resources is not its own");
    }

    @Test
    void countsTheStylesheetsItRead() {
        Path root = new GitFixture("selectors-counted").write(SHEET, WHOLE).root();
        assertEquals(1, new StylesheetSelectorCheck(root, RESOURCES).scanned(), "the count is what the module ships");
    }

    @Test
    void readsNothingButStylesheets() {
        Path root = new GitFixture("selectors-markup")
            .write("src/main/resources/templates/page.html", "<html></html>\n").root();
        assertEquals(0, new StylesheetSelectorCheck(root, RESOURCES).scanned(), "markup is another rule's to read");
    }

    private static List<String> offences(Path root) {
        return Verdicts.offences(findings(root), "joining two refusals with a space");
    }

    private static List<Findings> findings(Path root) {
        return new StylesheetSelectorCheck(root, RESOURCES).findings();
    }
}
