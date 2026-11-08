package eu.ciechanowiec.airness.governance;

import static eu.ciechanowiec.airness.governance.YamlSamples.assertClean;
import static eu.ciechanowiec.airness.governance.YamlSamples.assertDeviationAt;
import static org.junit.jupiter.api.Assertions.assertAll;

import org.junit.jupiter.api.Test;

/**
 * The lines whose indentation is not structure are passed over: block scalar text, flow collection lines,
 * scalar continuations, comments, dedents, and everything before a document marker.
 */
class YamlNestingPassageTest {

    @Test
    void passesOverTheTextOfEveryKindOfBlockScalar() {
        assertAll(
            () -> assertClean("run: |\n    echo one\n      indented: deeper\n   back: odd\nnext: 1\n"),
            () -> assertClean("script: >-\n     folded\n       text\n\n     after a blank\nnext: 1\n"),
            () -> assertClean("text: |2\n     kept\nnext: 1\n"),
            () -> assertClean("text: |+ # keep the newlines\n   kept\nnext: 1\n"),
            () -> assertClean("- run: |\n      echo\n         odd: 1\n  name: step\n- |\n   bare\n"),
            () -> assertClean("key: !!str |\n     tagged\nnext:\n  value: 1\n")
        );
    }

    @Test
    void measuresTheFirstLineAfterABlockScalarEnds() {
        assertDeviationAt(4, "run: |\n    echo\nnext:\n    value: 1\n");
    }

    @Test
    void passesOverTheLinesInsideAFlowCollectionThatSpansSeveral() {
        assertAll(
            () -> assertClean("args: [\n      first,\n   second\n]\nnext:\n  value: 1\n"),
            () -> assertClean("map: {\n     a: 1, # a comment ]\n  b: '}' }\nnext: 1\n"),
            () -> assertClean("- [\n    a,\n  b ]\n- {a: 1}\n"),
            () -> assertDeviationAt(5, "args: [\n      first\n]\nnext:\n     value: 1\n")
        );
    }

    @Test
    void passesOverTheLinesContinuingAPlainOrAQuotedScalar() {
        assertAll(
            () -> assertClean("description: a long\n      text that continues\nnext: 1\n"),
            () -> assertClean("title: \"a long\n     quoted # title\"\nnext: 1\n"),
            () -> assertClean("note: 'it''s # not a comment:'\n   continued\nnext: 1\n")
        );
    }

    @Test
    void passesOverCommentsAtAnyColumn() {
        assertAll(
            () -> assertClean("a:\n     # an odd comment\n  b: 1\n # another\n"),
            () -> assertClean("a: # a note after the key\n  b: 1\n")
        );
    }

    @Test
    void doesNotMeasureALineThatDedents() {
        assertClean("a:\n  b:\n    c: 1\nd: 1\n  e: 1\n");
    }

    @Test
    void startsAfreshAtEveryDocumentMarkerAndDirective() {
        assertAll(
            () -> assertClean("a:\n  b: 1\n---\nc:\n  d: 1\n...\n%YAML 1.2\n--- # a comment\ne: 1\n"),
            () -> assertClean("a:\n---\n   x: 1\n"),
            () -> assertDeviationAt(5, "a:\n  b: 1\n---\nc:\n    d: 1\n")
        );
    }
}
