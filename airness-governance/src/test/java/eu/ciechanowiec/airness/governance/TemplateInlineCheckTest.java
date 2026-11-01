package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateInlineCheckTest {

    private static final List<Path> RESOURCES = List.of(Path.of("src", "main", "resources"));

    private static final String TEMPLATE = "src/main/resources/templates/room/detail.html";

    private static String body(String written) {
        return """
            <!DOCTYPE html>
            <html lang="en" xmlns:th="http://www.thymeleaf.org">
            <body>
            %s
            </body>
            </html>
            """.formatted(written);
    }

    @Test
    void reportsAScriptBlockThatHoldsCode() {
        assertEquals(1, inline(fixture("inline-script", "<script>start();</script>")).size(), "it runs unchecked");
    }

    @Test
    void reportsAModuleScriptAndOneTypedAsJavascript() {
        Path root = fixture(
            "inline-module",
            "<script type=\"module\">start();</script>\n<script type=\"text/javascript\">go();</script>"
        );
        assertEquals(2, inline(root).size(), "both types are run by the browser");
    }

    @Test
    void reportsAStyleBlock() {
        Path root = fixture("inline-style", "<style>p { color: red; }</style>");
        assertEquals(1, inline(root).size(), "a style block is a stylesheet the stylesheet checks never read");
    }

    @Test
    void reportsAnEventHandlerAndTheDialectAttributeThatWritesOne() {
        Path root = fixture(
            "inline-handler",
            "<button onclick=\"go()\">Go</button>\n<button th:onclick=\"|go(${id})|\">Go</button>"
        );
        assertEquals(2, inline(root).size(), "a handler attribute is script, whoever writes it");
    }

    @Test
    void reportsAJavascriptAddress() {
        Path root = fixture("inline-address", "<a href=\" JavaScript:go()\">Go</a>");
        assertEquals(1, inline(root).size(), "a javascript: address runs its text as script");
    }

    @Test
    void reportsATemplateInlinedAsScriptOrAsAStylesheet() {
        Path root = fixture(
            "inline-dialect",
            "<div th:inline=\"javascript\">x</div>\n<div data-th-inline=\"css\">y</div>\n<p th:inline=\"text\">z</p>"
        );
        assertEquals(2, inline(root).size(), "inlining as text writes no code");
    }

    @Test
    void readsAnElementWhoseAttributeHasNoValue() {
        Path root = fixture(
            "inline-valueless",
            "<script async>start();</script>\n<button hidden onclick=\"go()\">Go</button>\n<details open></details>"
        );
        assertEquals(2, inline(root).size(), "an attribute written without a value hides nothing beside it");
    }

    @Test
    void leavesAScriptThatLoadsAFileAlone() {
        Path root = fixture("inline-sourced", "<script th:src=\"@{/js/app.js}\" defer></script>");
        assertTrue(Verdicts.clean(findings(root)), "a referenced file is checked where it lives");
    }

    @Test
    void leavesDataBlocksAlone() {
        Path root = fixture(
            "inline-data",
            """
                <script type="application/json" id="model">{"a":1}</script>
                <script type="application/ld+json">{}</script>
                <script type="importmap">{"imports":{}}</script>
                <script type="speculationrules">{}</script>
                """
        );
        assertTrue(Verdicts.clean(findings(root)), "the browser reads data blocks rather than running them");
    }

    @Test
    void leavesTemplateAndFrameworkSyntaxAlone() {
        Path root = fixture(
            "inline-syntax",
            """
                <div x-data="{ open: false }" @click="open = !open" x-on:keydown.escape="open = false">
                <button hx-get="/rows" hx-on:htmx:after-request="done()" th:text="${label}">x</button>
                <a th:href="@{/rooms/{id}(id=${room.id()})}">Room</a>
                </div>
                """
        );
        assertTrue(
            Verdicts.clean(findings(root)), "dialect attributes are template syntax the sweep assesses at runtime"
        );
    }

    @Test
    void reportsTheCodeUnderThePlaceItWasWrittenAndSaysWhereToMoveIt() {
        String offence = inline(fixture("inline-located", "<script>start();</script>")).getFirst();
        assertTrue(
            offence.startsWith("src/main/resources/templates/room/detail.html:4:")
                && offence.contains("Move it into a checked file"),
            "an offence names the file, the line and the repair"
        );
    }

    private static Path fixture(String name, String written) {
        return new GitFixture(name).write(TEMPLATE, body(written)).root();
    }

    private static List<String> inline(Path root) {
        return Verdicts.offences(findings(root), "inline in markup");
    }

    private static List<Findings> findings(Path root) {
        return new TemplateInlineCheck(root, RESOURCES).findings();
    }
}
