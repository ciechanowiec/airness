package eu.ciechanowiec.airness.governance;

import static eu.ciechanowiec.airness.governance.YamlSamples.assertClean;
import static eu.ciechanowiec.airness.governance.YamlSamples.assertDeviationAt;
import static eu.ciechanowiec.airness.governance.YamlSamples.first;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Each nesting step of a YAML file is the declared width, measured from where the opening line's node starts.
 */
class YamlNestingTest {

    @Test
    void passesAMappingNestedOneDeclaredStepAtATime() {
        assertClean("spring:\n  datasource:\n    url: x\n  jpa:\n    open-in-view: false\n");
    }

    @Test
    void reportsAFourColumnStepWhereTheDeclaredStepIsTwo() {
        assertEquals(
            Optional.of(
                new IndentationDeviation(2, "indent_size = 2, but line 2 nests 4 columns deeper than line 1")
            ),
            first("spring:\n    datasource:\n        url: x\n")
        );
    }

    @Test
    void reportsAThreeColumnStepWhereTheDeclaredStepIsTwo() {
        assertEquals(
            Optional.of(
                new IndentationDeviation(3, "indent_size = 2, but line 3 nests 3 columns deeper than line 2")
            ),
            first("spring:\n  datasource:\n     url: x\n")
        );
    }

    @Test
    void measuresTheStepAgainstTheSizeItIsGiven() {
        assertAll(
            () -> assertTrue(YamlNesting.first("a:\n    b: 1\n".lines().toList(), 4).isEmpty()),
            () -> assertTrue(YamlNesting.first("a:\n  b: 1\n".lines().toList(), 4).isPresent())
        );
    }

    @Test
    void passesASequenceOneStepUnderItsKeyAndOneWrittenAtTheColumnOfItsKey() {
        assertAll(
            () -> assertClean("steps:\n  - checkout\n  - build\n"),
            () -> assertClean("steps:\n- checkout\n- build\nnext: 1\n")
        );
    }

    @Test
    void reportsASequenceTwoStepsUnderItsKey() {
        assertDeviationAt(2, "steps:\n    - checkout\n");
    }

    @Test
    void readsTheLinesContinuingASequenceEntryAsItsSiblings() {
        assertAll(
            () -> assertClean(
                "steps:\n  - name: Build\n    run: mvn verify\n    with:\n      java: 25\n  - name: Test\n"
            ),
            () -> assertClean("-   name: Build\n    run: mvn verify\n"),
            () -> assertDeviationAt(3, "- name: Build\n  with:\n      java: 25\n")
        );
    }

    @Test
    void measuresTheStepUnderAnEntryThatOpensAMappingFromItsKey() {
        assertAll(
            () -> assertClean("- with:\n    java: 25\n- with:\n  - java\n"),
            () -> assertDeviationAt(2, "- with:\n      java: 25\n")
        );
    }

    @Test
    void measuresTheStepUnderABareDashFromTheDash() {
        assertAll(
            () -> assertClean("-\n  name: Build\n  run: mvn\n- - nested\n  - sibling\n"),
            () -> assertDeviationAt(5, "items:\n  -\n    name: Build\n  -\n      name: Test\n")
        );
    }

    @Test
    void readsAKeyFollowedOnlyByAnAnchorOrATagAsOpeningABlock() {
        assertAll(
            () -> assertClean("base: &base\n  a: 1\nother: !!map\n  b: 1\nboth: &x !!map\n  c: 1\n"),
            () -> assertClean("merged:\n  <<: *base\n  d: 1\n"),
            () -> assertDeviationAt(2, "base: &base\n    a: 1\n"),
            () -> assertDeviationAt(2, "- &entry\n    a: 1\n")
        );
    }

    @Test
    void readsTheMappingIndicatorOutsideQuotedKeysAndValues() {
        assertAll(
            () -> assertClean("\"a: b\":\n  c: 1\n'd: e': f\n"),
            () -> assertClean("url: http://example.invalid:8080\n   continued\nimage: postgres:17.5\n"),
            () -> assertDeviationAt(2, "\"a: b\":\n    c: 1\n")
        );
    }

    @Test
    void reportsOnlyTheFirstStepOfAnotherWidth() {
        assertDeviationAt(2, "a:\n    b:\n         c: 1\n");
    }
}
