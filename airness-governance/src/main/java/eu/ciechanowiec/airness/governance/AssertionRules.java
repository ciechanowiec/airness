package eu.ciechanowiec.airness.governance;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;

/**
 * Reads a test source and reports the two ways a test can pass while proving nothing.
 *
 * <p>The first is a test that reaches no assertion. Such a test drives the code and then declines to
 * judge what came back, so it can only fail by throwing. It is worse than an absent test, because the
 * coverage floor counts every line it ran and the log records a pass, and the two together read as
 * evidence that the behaviour was checked.
 *
 * <p>The second is an assertion whose operands are literals alone. Its verdict is settled the moment it
 * is written, so no change to the code under test can move it. That is the same absence of evidence as
 * the first, dressed as its opposite.
 *
 * <p>An assertion reached through a helper counts, because a suite that names its assertions reads
 * better than one that inlines them. The search follows a call to any method the same file declares and
 * stops at the file boundary. A test that keeps every assertion in another type therefore reports as
 * unproven, which is answered by moving the assertion back or by a suppression that says why not.
 */
@UtilityClass
final class AssertionRules {

    private static final Pattern ANNOTATION = Pattern.compile(
        "@(?:Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\\b"
    );
    /*
     * A name, a parameter list that may nest one level of parentheses, an optional throws clause, then
     * the brace that opens a body. The keywords are excluded because a control structure has the same
     * shape as a call and would otherwise be collected as a method named "if" or "for".
     *
     * The parameter list consumes runs rather than single characters, which is what keeps the scan off
     * the stack. A matcher spends a frame on every turn of a group it repeats, so an alternation over one
     * character at a time costs a frame per character read and overflows on a long source, whichever
     * source happens to be the longest on the day. Written as runs, a turn costs one frame per token of
     * the list instead, and the depth stops following the size of the file. The lexer of JavaCode is
     * written the same way, for the same reason.
     */
    private static final Pattern SIGNATURE = Pattern.compile(
        "\\b(?!if\\b|for\\b|while\\b|switch\\b|catch\\b|synchronized\\b|do\\b|else\\b|return\\b|new\\b)"
            + "(\\w+)\\s*\\((?:[^()]++|\\([^()]*+\\))*+\\)\\s*(?:throws[^{;]*)?\\{"
    );
    private static final Pattern ASSERTION = Pattern.compile("\\b(?:assert\\w*|fail|expect\\w*|verify\\w*)\\s*\\(");
    private static final Pattern CALL = Pattern.compile("\\b(\\w+)\\s*\\(");
    /*
     * An operand written out rather than computed. A call whose operands are all of this shape compares
     * one constant with another, whatever the code under test does.
     *
     * The alternation is left ungrouped here and wrapped at each place it is spliced in. A group around
     * the whole constant reads as redundant while the constant stands alone, and is required the moment
     * it sits beside anything else, so the group belongs where its necessity is visible.
     */
    private static final String LITERAL
        = "\"(?:[^\"\\\\]|\\\\.)*\"|'(?:[^'\\\\]|\\\\.)*'|true|false|null|-?\\d[\\w.]*";
    /*
     * A message argument may follow the operands, and only as a literal of its own. Allowing any third
     * argument would read the JUnit 4 order, where the message comes first, as a pair of settled
     * operands and report every assertion written that way.
     */
    private static final String MESSAGE = "(?:\\s*,\\s*\"(?:[^\"\\\\]|\\\\.)*\")?\\s*\\)";
    private static final Pattern SETTLED = Pattern.compile(
        "\\bassertTrue\\s*\\(\\s*true" + MESSAGE
            + "|\\bassertFalse\\s*\\(\\s*false" + MESSAGE
            + "|\\bassertNull\\s*\\(\\s*null" + MESSAGE
            + "|\\bassertNotNull\\s*\\(\\s*(?:" + LITERAL + ")\\s*\\)"
            + "|\\bassert(?:Equals|NotEquals|Same|NotSame)\\s*\\(\\s*(?:"
            + LITERAL + ")\\s*,\\s*(?:" + LITERAL + ")" + MESSAGE
    );
    private static final char OPENING = '{';
    private static final char CLOSING = '}';

    /**
     * Every test in the source that reaches no assertion, directly or through a helper beside it.
     *
     * @param source the decoded text of a Java source
     * @return one entry per unproven test, naming its line and its name
     */
    static List<String> unproven(CharSequence source) {
        String code = JavaCode.blanked(source);
        Map<String, String> bodies = bodies(code);
        return cases(code).stream()
            .filter(unit -> !reaches(unit.in(code), bodies, new TreeSet<>(Set.of(unit.name()))))
            .map(unit -> "line %d: %s".formatted(JavaCode.lineOf(code, unit.start()), unit.name()))
            .toList();
    }

    /**
     * Every assertion in the source whose operands are literals alone.
     *
     * <p>The scan reads the source with its one-line literals intact, because the operands are the
     * subject, and with its text blocks blanked, because a fixture quoting a source is not code of the
     * file that quotes it.
     *
     * <p>Keeping one-line literals is what lets the operands be read, and it is also what would let a
     * one-line literal that quotes an assertion be read as one. A test that asserts something about the
     * text "assertEquals(1, 1)" is not a test that asserts 1 equals 1. Each hit is therefore confirmed
     * against the fully blanked form of the same source, where every literal is blank and only code
     * survives. Both forms keep the width and the line breaks of the original, so one offset addresses
     * both, and a hit that lands on blank there was quoted rather than written.
     *
     * @param source the decoded text of a Java source
     * @return one entry per assertion that cannot fail, naming its line and the call
     */
    static List<String> settled(CharSequence source) {
        String readable = JavaCode.withoutComments(source);
        String code = JavaCode.blanked(source);
        return SETTLED.matcher(readable).results()
            .filter(hit -> isCode(code, hit.start()))
            .map(hit -> "line %d: %s".formatted(JavaCode.lineOf(readable, hit.start()), hit.group().strip()))
            .toList();
    }

    private static boolean isCode(CharSequence blanked, int start) {
        return start < blanked.length() && !Character.isWhitespace(blanked.charAt(start));
    }

    private static boolean reaches(CharSequence body, Map<String, String> bodies, Set<String> visited) {
        return ASSERTION.matcher(body).find() || CALL.matcher(body).results()
            .map(call -> call.group(1))
            .filter(visited::add)
            .filter(bodies::containsKey)
            .anyMatch(called -> reaches(bodies.getOrDefault(called, ""), bodies, visited));
    }

    private static List<CaseBody> cases(CharSequence code) {
        return ANNOTATION.matcher(code).results()
            .map(hit -> caseAfter(code, hit.end()))
            .flatMap(Optional::stream)
            .collect(Collectors.toMap(CaseBody::start, unit -> unit, (first, _) -> first, TreeMap::new))
            .values()
            .stream()
            .toList();
    }

    private static Optional<CaseBody> caseAfter(CharSequence code, int from) {
        Matcher signature = SIGNATURE.matcher(code);
        if (!signature.find(from)) {
            return Optional.empty();
        }
        int opening = signature.end() - 1;
        String name = signature.group(1);
        return closingBrace(code, opening).stream()
            .mapToObj(closing -> new CaseBody(name, opening + 1, closing))
            .findFirst();
    }

    private static Map<String, String> bodies(CharSequence code) {
        return SIGNATURE.matcher(code).results()
            .map(signature -> bodyOf(code, signature))
            .flatMap(Optional::stream)
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, String::concat, TreeMap::new));
    }

    private static Optional<Map.Entry<String, String>> bodyOf(CharSequence code, MatchResult signature) {
        int opening = signature.end() - 1;
        return closingBrace(code, opening).stream()
            .mapToObj(closing -> Map.entry(signature.group(1), code.subSequence(opening + 1, closing).toString()))
            .findFirst();
    }

    /*
     * Counts from the opening brace, which takes the depth to one, and stops where it returns to zero.
     * Every brace inside a literal or a comment was blanked before this ran, so the only braces left are
     * the ones the compiler pairs.
     */
    private static OptionalInt closingBrace(CharSequence code, int opening) {
        int depth = 0;
        for (int index = opening; index < code.length(); index += 1) {
            depth += step(code.charAt(index));
            if (depth == 0) {
                return OptionalInt.of(index);
            }
        }
        return OptionalInt.empty();
    }

    private static int step(char character) {
        return switch (character) {
            case OPENING -> 1;
            case CLOSING -> -1;
            default -> 0;
        };
    }
}
