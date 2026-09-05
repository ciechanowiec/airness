package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Every rule that scans a source reads one that is long.
 *
 * <p>These rules read text with regular expressions, and a matcher spends a stack frame on each turn of
 * a group it repeats. A group written to consume one character at a time therefore costs a frame per
 * character, so the depth of the scan follows the length of the source and the deepest source in a
 * project decides whether the build stands. That failure arrives as a {@link StackOverflowError} from inside a
 * matcher, on a commit that changed neither the rule nor the file that broke it, which is why it is
 * pinned here rather than left to the next long file someone writes.
 *
 * <p>Each test below reads through {@link BoundedStack}, so the stack is named rather than inherited,
 * and against an input far longer than the fixtures the rule's own tests use. What is asserted is that
 * the rule answered at all, and that the answer is the one the input deserves.
 */
class LongSourceTest {

    private static final int WIDE = 20_000;
    private static final int MANY = 800;

    @Test
    void blanksALiteralThatFillsALine() {
        String source = """
            package sample;

            class Subject {

                private static final String LONG = "%s";
            }
            """.formatted("x".repeat(WIDE));

        Optional<String> blanked = BoundedStack.read(() -> JavaCode.blanked(source));

        assertTrue(blanked.isPresent(), "every rule in this package reads its source through the lexer");
        assertEquals(
            source.length(), blanked.orElseThrow().length(),
            "and the form it returns keeps the width of what it read, however wide that was"
        );
    }

    @Test
    void readsAnAssertionCarryingALongMessage() {
        String source = """
            package sample;

            class SubjectTest {

                @Test
                void addsTheTwoNumbersGiven() {
                    assertEquals(4, new Subject().sum(2, 2), "%s");
                }
            }
            """.formatted("a message written at length ".repeat(MANY));

        Optional<List<String>> settled = BoundedStack.read(() -> AssertionRules.settled(source));

        assertTrue(settled.isPresent(), "the operands are read out of the call, message and all");
        assertEquals(
            List.of(), settled.orElseThrow(),
            "and the second operand is a value the subject produced, so nothing here is settled"
        );
    }

    @Test
    void readsASuppressionExplainedAtLength() {
        String source = """
            package sample;

            class Subject {

                @SuppressWarnings(value = "AirnessRule", justification = "%s")
                int value() {
                    return 0;
                }
            }
            """.formatted("a reason written at length ".repeat(MANY));

        Optional<List<String>> suppressed = BoundedStack.read(() -> Suppressions.in(source));

        assertTrue(suppressed.isPresent(), "a reason is as long as the rule it explains needs it to be");
        assertEquals(1, suppressed.orElseThrow().size(), "and one rule is suppressed however long the reason ran");
    }

    @Test
    void readsAMappingThatListsManyPaths() {
        String source = """
            package sample;

            @RestController
            class Rooms {

                @GetMapping(path = {%s"/rooms"})
                public String all() {
                    return "";
                }
            }
            """.formatted("\"/rooms\", ".repeat(MANY));

        Optional<List<String>> unbound = BoundedStack.read(() -> SpringWebRules.unboundPathVariables(source));

        assertTrue(unbound.isPresent(), "the paths of a mapping are read out of the array that lists them");
        assertEquals(
            List.of(), unbound.orElseThrow(),
            "and the handler takes no path variable, so none of them is left unbound"
        );
    }

    @Test
    void readsAQueryHoldingALongLiteral() {
        String source = """
            package sample;

            interface Rooms {

                @Query("SELECT r FROM Room r WHERE r.name = '%s'")
                List<Room> named();
            }
            """.formatted("x".repeat(WIDE));

        Optional<List<String>> positional = BoundedStack.read(() -> SpringQueryParameterRules.positional(source));

        assertTrue(positional.isPresent(), "the literals of a query are masked before its bindings are read");
        assertEquals(
            List.of(), positional.orElseThrow(),
            "and the query binds nothing by position, whatever its literal holds"
        );
    }
}
