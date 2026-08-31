package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * How a fragment expression is read, apart from any document that writes one.
 */
class TemplateCallExpressionsTest {

    @Test
    void readsTheTemplateAndTheFragmentAnExpressionNames() {
        FragmentCall call = only("~{layout/page :: page(a, b, c)}");
        assertEquals("layout/page", call.template(), "the half before the separator names the template");
    }

    @Test
    void readsTheFragmentName() {
        assertEquals("page", only("~{layout/page :: page(a, b, c)}").fragment(), "the name precedes the list");
    }

    @Test
    void countsTheArgumentsHandedOver() {
        assertEquals(3, only("~{layout/page :: page(a, b, c)}").arguments(), "three arguments are handed over");
    }

    @Test
    void countsANestedExpressionAsOneArgument() {
        FragmentCall call = only("~{layout/page :: page('Rooms', 'rooms', ~{:: #page-body})}");
        assertEquals(3, call.arguments(), "an expression inside the list is one argument rather than a call");
    }

    @Test
    void readsNoTemplateForACallOnItsOwnDocument() {
        assertTrue(only("~{:: step('Next', ${ahead})}").local(), "a call naming nothing before the separator");
    }

    @Test
    void readsNoTemplateForACallNamingItself() {
        assertTrue(only("~{this :: step(a, b)}").local(), "naming this is naming the document it was written in");
    }

    @Test
    void readsNoFragmentForACallOnAWholeTemplate() {
        assertTrue(only("~{fragments/head}").whole(), "an expression with no separator reaches a whole template");
    }

    @Test
    void readsACallWrittenWithoutItsBraces() {
        assertEquals("fragments/head", only("fragments/head :: head(title)").template(), "the braces are optional");
    }

    @Test
    void passesOverANameTheExpressionBuilds() {
        assertEquals(List.of(), TemplateCallRules.calls("${content}"), "a built name is not a fact about a source");
    }

    @Test
    void passesOverATemplateNameTheExpressionBuilds() {
        assertEquals(List.of(), TemplateCallRules.calls("~{${page} :: body}"), "a built template names nothing");
    }

    @Test
    void passesOverASelectorReachingAnElement() {
        assertEquals(List.of(), TemplateCallRules.calls("~{:: #page-body}"), "a selector reaches an element");
    }

    @Test
    void keepsACallWhoseArgumentsAreExpressions() {
        assertEquals(1, TemplateCallRules.calls("~{a/b :: c(${one}, ${two})}").size(), "arguments may be built");
    }

    @Test
    void readsBothCallsAnExpressionChoosesBetween() {
        List<FragmentCall> calls = TemplateCallRules.calls("${on} ? ~{a :: one} : ~{b :: two}");
        assertEquals(2, calls.size(), "a value choosing between two fragments names both of them");
    }

    @Test
    void readsNothingOutOfAnExpressionLeftOpen() {
        assertEquals(List.of(), TemplateCallRules.calls("~{a/b :: c("), "an unclosed expression names nothing");
    }

    @Test
    void readsEveryAttributeThatTakesAFragmentExpression() {
        assertTrue(
            TemplateCallRules.caller("th:insert") && TemplateCallRules.caller("data-th-replace"),
            "insert and the data spelling take one as readily as replace does"
        );
    }

    @Test
    void readsNoFragmentExpressionOutOfAnOrdinaryAttribute() {
        assertFalse(TemplateCallRules.caller("th:text"), "an attribute that takes no fragment expression");
    }

    private static FragmentCall only(String expression) {
        return TemplateCallRules.calls(expression).getFirst();
    }
}
