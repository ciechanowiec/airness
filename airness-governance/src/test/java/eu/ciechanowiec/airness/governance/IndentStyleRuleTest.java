package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.ec4j.core.model.PropertyType.IndentStyleValue;
import org.junit.jupiter.api.Test;

/**
 * The whitespace that opens a line is the kind {@code indent_style} names, wherever in it the wrong kind sits.
 */
class IndentStyleRuleTest {

    @Test
    void reportsATabThatOpensALineUnderTheSpaceStyle() {
        assertEquals(
            Optional.of(new IndentationDeviation(2, "indent_style = space, but line 2 indents with a tab")),
            first("first\n\tsecond\n\tthird\n", IndentStyleValue.space)
        );
    }

    @Test
    void reportsATabAfterSpacesUnderTheSpaceStyle() {
        assertEquals(
            Optional.of(new IndentationDeviation(3, "indent_style = space, but line 3 indents with a tab")),
            first("first\n    second\n  \tthird\n", IndentStyleValue.space)
        );
    }

    @Test
    void passesSpacesAndTabsThatFollowTheIndentationUnderTheSpaceStyle() {
        assertEquals(Optional.empty(), first("first\n    second\tcolumn\n\n", IndentStyleValue.space));
    }

    @Test
    void reportsASpaceThatOpensALineUnderTheTabStyle() {
        assertAll(
            () -> assertEquals(
                Optional.of(new IndentationDeviation(2, "indent_style = tab, but line 2 indents with a space")),
                first("target:\n    recipe\n", IndentStyleValue.tab)
            ),
            () -> assertEquals(
                Optional.of(new IndentationDeviation(1, "indent_style = tab, but line 1 indents with a space")),
                first(" \trecipe\n", IndentStyleValue.tab)
            )
        );
    }

    @Test
    void passesSpacesThatAlignAfterTheTabsUnderTheTabStyle() {
        assertEquals(
            Optional.empty(),
            first("target:\n\trecipe \\\n\t      continued\n\n", IndentStyleValue.tab)
        );
    }

    private static Optional<IndentationDeviation> first(String text, IndentStyleValue style) {
        return IndentStyleRule.first(text.lines().toList(), style);
    }
}
