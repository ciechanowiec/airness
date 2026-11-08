package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/**
 * The parts of a YAML line that decide its structure are read outside its quoted spans and its comment.
 */
class YamlTextTest {

    @Test
    void cutsACommentOnlyWhereWhitespaceOrTheLineStartPrecedesIt() {
        assertAll(
            () -> assertEquals("key: value", YamlText.uncommented("key: value   # a comment")),
            () -> assertEquals("", YamlText.uncommented("# a comment")),
            () -> assertEquals("color: a#b", YamlText.uncommented("color: a#b")),
            () -> assertEquals("key: \"a # b\"", YamlText.uncommented("key: \"a # b\" # c")),
            () -> assertEquals("key: 'it''s # here'", YamlText.uncommented("key: 'it''s # here'")),
            () -> assertEquals("key: it's", YamlText.uncommented("key: it's # 'quoted'"))
        );
    }

    @Test
    void readsTheMappingIndicatorOutsideQuotedSpans() {
        assertAll(
            () -> assertEquals(OptionalInt.of(3), YamlText.mappingIndicator("key: value")),
            () -> assertEquals(OptionalInt.of(3), YamlText.mappingIndicator("key:")),
            () -> assertEquals(OptionalInt.of(6), YamlText.mappingIndicator("\"a: b\": c")),
            () -> assertEquals(OptionalInt.of(5), YamlText.mappingIndicator("image: postgres:17.5")),
            () -> assertEquals(OptionalInt.empty(), YamlText.mappingIndicator("http://example.invalid")),
            () -> assertEquals(OptionalInt.empty(), YamlText.mappingIndicator("'a: b'"))
        );
    }

    @Test
    void stepsPastEverySequenceEntryThatOpensANode() {
        assertAll(
            () -> assertEquals(2, YamlText.nodeStart("- item", 0)),
            () -> assertEquals(6, YamlText.nodeStart("  - - item", 2)),
            () -> assertEquals(4, YamlText.nodeStart("-   item", 0)),
            () -> assertEquals(1, YamlText.nodeStart("-", 0)),
            () -> assertEquals(0, YamlText.nodeStart("-1", 0)),
            () -> assertEquals(0, YamlText.nodeStart("key: value", 0))
        );
    }

    @Test
    void stripsTheAnchorsAndTagsAheadOfAValue() {
        assertAll(
            () -> assertEquals("", YamlText.withoutProperties(" &base")),
            () -> assertEquals("|", YamlText.withoutProperties("&base !!str |")),
            () -> assertEquals("value", YamlText.withoutProperties("!tag value")),
            () -> assertEquals("*base", YamlText.withoutProperties("*base"))
        );
    }

    @Test
    void recognizesEveryBlockScalarHeader() {
        assertAll(
            () -> assertTrue(YamlText.blockScalar("|")),
            () -> assertTrue(YamlText.blockScalar(">")),
            () -> assertTrue(YamlText.blockScalar(">-")),
            () -> assertTrue(YamlText.blockScalar("|+")),
            () -> assertTrue(YamlText.blockScalar("|2")),
            () -> assertTrue(YamlText.blockScalar("|2-")),
            () -> assertFalse(YamlText.blockScalar("| pipe")),
            () -> assertFalse(YamlText.blockScalar("value"))
        );
    }

    @Test
    void countsTheFlowCollectionsATextLeavesOpen() {
        assertAll(
            () -> assertTrue(YamlText.flow("[a,")),
            () -> assertTrue(YamlText.flow("{a: 1}")),
            () -> assertFalse(YamlText.flow("")),
            () -> assertFalse(YamlText.flow("a [b")),
            () -> assertEquals(1, YamlText.balance("[a, {b: 1},")),
            () -> assertEquals(0, YamlText.balance("[\"]\", ']'] # [")),
            () -> assertEquals(-1, YamlText.balance("  b }"))
        );
    }

    @Test
    void recognizesTheLinesThatCarryNoStructure() {
        assertAll(
            () -> assertTrue(YamlText.insignificant("   ")),
            () -> assertTrue(YamlText.insignificant("    # comment")),
            () -> assertFalse(YamlText.insignificant("key: # comment")),
            () -> assertTrue(YamlText.marker("---")),
            () -> assertTrue(YamlText.marker("--- # document")),
            () -> assertTrue(YamlText.marker("...")),
            () -> assertTrue(YamlText.marker("%YAML 1.2")),
            () -> assertFalse(YamlText.marker("----")),
            () -> assertFalse(YamlText.marker("  ---")),
            () -> assertEquals(4, YamlText.indent("    key: value"))
        );
    }
}
