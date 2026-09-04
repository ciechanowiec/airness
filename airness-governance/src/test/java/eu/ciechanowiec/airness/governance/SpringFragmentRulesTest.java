package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A fragment is declared in one document and reached from another, or from a name the Java writes, so
 * the module check reads both trees before deciding that nothing reaches one. What mentions a fragment
 * exempts it wherever the mention is written, and only a fragment nothing mentions at all is reported.
 */
class SpringFragmentRulesTest {

    private static final List<Path> SOURCES
        = List.of(Path.of("src/main/java"), Path.of("src/test/java"));

    private static final List<Path> RESOURCES = List.of(Path.of("src/main/resources"));

    private static final String PARTS = "src/main/resources/templates/fragments/parts.html";

    private static final String PAGE = "src/main/resources/templates/catalogue/list.html";

    private static final String SOURCE = "src/main/java/sample/Catalogue.java";

    private static final String UNREACHED = "that nothing in it reaches";

    private static final String TOAST = """
        <!DOCTYPE html>
        <html lang="en" xmlns:th="http://www.thymeleaf.org">
        <body><div th:fragment="toast(text)">Said</div></body>
        </html>
        """;

    private static final String NOTHING = "";

    @Test
    void leavesAFragmentACallInTheMarkupReaches() {
        assertTrue(
            Verdicts.clean(findings("parts-called", "th:replace=\"~{fragments/parts :: toast('Hi')}\"", NOTHING)),
            "a call in the markup is the ordinary way a fragment is reached"
        );
    }

    @Test
    void leavesAFragmentAConstantOfTheModuleNames() {
        assertTrue(
            Verdicts.clean(findings("parts-named", NOTHING, "private static final String SAID = \"toast\";")),
            "a view name assembled from constants states the fragment in one of them"
        );
    }

    @Test
    void leavesAFragmentNamedInsideALongerViewString() {
        assertTrue(
            Verdicts.clean(
                findings("parts-joined", NOTHING, "private static final String SAID = \"catalogue/list :: toast\";")
            ),
            "a name written whole inside a view string mentions the fragment"
        );
    }

    @Test
    void reportsAFragmentNothingInTheModuleMentions() {
        assertEquals(
            1, offences("parts-dead", NOTHING, NOTHING).size(),
            "a fragment no call names and no string mentions is rendered by nothing"
        );
    }

    @Test
    void saysWhereTheDeadFragmentWasDeclared() {
        String only = offences("parts-where", NOTHING, NOTHING).getFirst();
        assertTrue(
            only.startsWith("src/main/resources/templates/fragments/parts.html:")
                && only.contains("declares the fragment toast"),
            "an offence names the document, the position and the fragment"
        );
    }

    @Test
    void countsACallWrittenInTheSpellingThatKeepsAPageValidHtml() {
        assertTrue(
            Verdicts.clean(
                findings("parts-data", "data-th-replace=\"~{fragments/parts :: toast('Hi')}\"", NOTHING)
            ),
            "a document staying valid HTML calls a fragment the same way"
        );
    }

    @Test
    void countsACallThatReachesTheDocumentItWasWrittenIn() {
        assertTrue(
            Verdicts.clean(findings("parts-this", "th:replace=\"~{this :: toast('Hi')}\"", NOTHING)),
            "a name is read from the call rather than from the document it resolves to"
        );
    }

    @Test
    void countsACallThatNamesNoTemplateAtAll() {
        assertTrue(
            Verdicts.clean(findings("parts-bare", "th:replace=\":: toast('Hi')\"", NOTHING)),
            "a call naming no template still names the fragment"
        );
    }

    // The shape every page of a layout-driven project writes: the page hands its own controls to a
    // shared header, which puts them in place through the variable it was handed. Nothing calls those
    // controls by name anywhere else, and reading only the calls a value makes reported every one.
    @Test
    void countsAFragmentHandedToAnotherFragmentAsAnArgument() {
        assertTrue(
            Verdicts.clean(
                findings(
                    "parts-handed",
                    "th:replace=\"~{fragments/parts :: header('Catalogue', ~{:: toast})}\"",
                    NOTHING
                )
            ),
            "a fragment handed over is reached, whatever puts it in place afterwards"
        );
    }

    @Test
    void countsAMentionWrittenInATestSource() {
        Path root = new GitFixture("parts-tested")
            .write(PARTS, TOAST)
            .write(SOURCE, source(NOTHING))
            .write("src/test/java/sample/CatalogueTest.java", source("private static final String SAID = \"toast\";"))
            .root();
        assertTrue(
            Verdicts.clean(verdicts(root)),
            "evidence of reach is taken wherever it is written, and a test naming a view is evidence"
        );
    }

    @Test
    void refusesALongerWordAsAMentionOfTheFragment() {
        assertEquals(
            1,
            offences("parts-longer", NOTHING, "private static final String SAID = \"toasted\";").size(),
            "a word merely opening with the name is another word"
        );
    }

    @Test
    void readsPastANameThatOnlyACommentCarries() {
        assertEquals(
            1, offences("parts-commented", NOTHING, "// the toast fragment is drawn by nothing").size(),
            "a comment saying the name is not the module reaching it"
        );
    }

    @Test
    void readsNothingOutOfMarkupNoEngineCouldRead() {
        Path root = new GitFixture("parts-unreadable")
            .write(PARTS, "<html><body><div th:fragment=\"toast(text)\" class=\"open></body></html>")
            .write(SOURCE, source(NOTHING))
            .root();
        assertTrue(
            Verdicts.clean(verdicts(root)),
            "markup no engine could read is the parse rule's finding rather than this one's"
        );
    }

    @Test
    void reportsOnlyTheFragmentThatNothingReaches() {
        Path root = new GitFixture("parts-one-of-two")
            .write(
                PARTS,
                """
                    <!DOCTYPE html>
                    <html lang="en" xmlns:th="http://www.thymeleaf.org">
                    <body>
                    <div th:fragment="modal(title)">Asked</div>
                    <div th:fragment="toast(text)">Said</div>
                    </body>
                    </html>
                    """
            )
            .write(PAGE, page("th:replace=\"~{fragments/parts :: modal('Sure?')}\""))
            .write(SOURCE, source(NOTHING))
            .root();
        List<String> offences = Verdicts.offences(verdicts(root), UNREACHED);
        assertEquals(1, offences.size(), "the called fragment of the document is left alone");
        assertTrue(offences.getFirst().contains("toast"), "the uncalled one is the one reported");
    }

    @Test
    void reportsAFragmentReachedOnlyByAnExpressionTheMarkupBuilds() {
        assertEquals(
            1, offences("parts-built", "th:replace=\"${chosen}\"", NOTHING).size(),
            "a name the markup builds is no name, so such a fragment is exempted by the Java that states it"
        );
    }

    private static List<String> offences(String name, String call, String java) {
        return Verdicts.offences(findings(name, call, java), UNREACHED);
    }

    private static List<Findings> findings(String name, String call, String java) {
        Path root = new GitFixture(name)
            .write(PARTS, TOAST)
            .write(PAGE, page(call))
            .write(SOURCE, source(java))
            .root();
        return verdicts(root);
    }

    private static String page(String call) {
        return """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body><div %s>Listed</div></body>
            </html>
            """.formatted(call);
    }

    private static String source(String written) {
        return """
            package sample;

            class Catalogue {

                %s

                int size() {
                    return 0;
                }
            }
            """.formatted(written);
    }

    private static List<Findings> verdicts(Path root) {
        return List.of(
            new Findings(
                UNREACHED, Verdicts.offences(
                    new SpringModuleCheck(root, SOURCES, RESOURCES).findings(), UNREACHED
                )
            )
        );
    }
}
