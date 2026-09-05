package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateExpressionRulesTest {

    private static final String PILL = "~{fragments/status-pill :: pill(%s, ${item.offered()} ? 'a' : 'b')}";

    @Test
    void readsACallWrittenWithNoExpressionAroundItAtAll() {
        assertEquals(List.of("words.of"), TemplateExpressionRules.calls("words.of('a')"));
    }

    @Test
    void readsBothArmsOfAConditionalWrittenOutsideTheExpression() {
        assertEquals(
            List.of("words.of", "words.of"),
            TemplateExpressionRules.calls("${a} ? words.of('x') : words.of('y')"),
            "each arm is a call the engine will not evaluate where it stands"
        );
    }

    @Test
    void readsACallWrittenInTheArgumentListOfAFragmentExpression() {
        String written = PILL.formatted("${a} ? words.of('x') : words.of('y')");
        assertEquals(2, TemplateExpressionRules.calls(written).size(), "an argument list is evaluated");
    }

    @Test
    void readsAUtilityObjectCalledOutsideAnExpression() {
        assertEquals(List.of("#strings.toUpperCase"), TemplateExpressionRules.calls("#strings.toUpperCase(a)"));
    }

    @Test
    void leavesACallInsideAVariableExpressionAlone() {
        assertTrue(
            TemplateExpressionRules.calls("${a ? words.of('x') : words.of('y')}").isEmpty(),
            "a variable expression is what evaluates a call"
        );
    }

    @Test
    void leavesALiteralWrittenOutsideAnExpressionAlone() {
        assertTrue(
            TemplateExpressionRules.calls("${a} ? 'Offered' : 'Withdrawn'").isEmpty(),
            "a literal is written out rather than worked out"
        );
    }

    @Test
    void leavesTheNameAFragmentExpressionReachesFor() {
        assertTrue(
            TemplateExpressionRules.calls(PILL.formatted("${words.of(a)}")).isEmpty(),
            "a fragment name is written the way a call is written and is none"
        );
    }

    @Test
    void leavesTheKeyAMessageExpressionReachesFor() {
        assertTrue(
            TemplateExpressionRules.calls("#{pagination.page(${page}, ${pages})}").isEmpty(),
            "a message key takes arguments without being a call"
        );
    }

    @Test
    void leavesThePathVariablesAndParametersOfALinkAlone() {
        assertTrue(
            TemplateExpressionRules.calls("@{/rooms/{reference}/seating(reference=${reference})}").isEmpty(),
            "a link writes a path variable in braces and its parameters after the path"
        );
    }

    @Test
    void leavesACallWrittenInsideALiteralSubstitutionAlone() {
        assertTrue(
            TemplateExpressionRules.calls("|showDialog(${id})|").isEmpty(),
            "a literal substitution writes its contents out, so a call in one is text"
        );
    }

    @Test
    void leavesACallWrittenInsideAQuotedLiteralAlone() {
        assertTrue(TemplateExpressionRules.calls("'showDialog(' + ${id} + ')'").isEmpty(), "a quoted literal is text");
    }

    @Test
    void leavesASelectionExpressionAlone() {
        assertTrue(
            TemplateExpressionRules.calls("*{profile.name()}").isEmpty(),
            "a selection expression evaluates what it carries"
        );
    }

    @Test
    void leavesAnIterationAlone() {
        assertTrue(TemplateExpressionRules.calls("item, stat : ${items}").isEmpty(), "an iteration names a variable");
    }

    @Test
    void leavesAUtilityObjectCalledInsideAnExpressionAlone() {
        assertTrue(
            TemplateExpressionRules.calls("${#numbers.formatDecimal(a, 1, 2)}").isEmpty(),
            "a utility object is called where calls are evaluated"
        );
    }

    @Test
    void leavesANestedFragmentExpressionAlone() {
        assertTrue(
            TemplateExpressionRules.calls("~{layout/page :: page(${t.u()}, 'x', ~{:: #body})}").isEmpty(),
            "a fragment handed to a fragment names two fragments and calls neither"
        );
    }

    @Test
    void leavesAValueThatWritesNoExpressionAtAllAlone() {
        assertTrue(TemplateExpressionRules.calls("positive").isEmpty(), "a token is written out");
    }

    @Test
    void leavesTheSampleContentOfAnElementAlone() {
        assertTrue(
            TemplateExpressionRules.inlined("Lunch (Per person, EUR 25.00)").isEmpty(),
            "text outside an inlining mark is written out as it stands, brackets and all"
        );
    }

    @Test
    void readsACallWrittenInsideAnInliningMark() {
        assertEquals(
            List.of("words.of"),
            TemplateExpressionRules.inlined("[[${a} ? words.of('x') : 'y']]"),
            "what an inlining mark encloses is an expression like any other"
        );
    }

    @Test
    void readsACallWrittenInsideAnUnescapedInliningMark() {
        assertEquals(1, TemplateExpressionRules.inlined("[(${a} ? words.of('x') : 'y')]").size());
    }

    @Test
    void readsACallWrittenAfterALiteralSubstitutionHasClosed() {
        assertEquals(
            List.of("words.of"), TemplateExpressionRules.calls("|${a}| + words.of('x')"),
            "a substitution ends at its closing mark"
        );
    }
}
