package eu.ciechanowiec.airness.maven;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import lombok.experimental.UtilityClass;

/**
 * Recognizes compound SPDX declaration syntax without turning free-text license names into expressions.
 */
@UtilityClass
final class ReportSpdxSyntax {

    private static final Pattern TOKENS = Pattern.compile("[()]|[^\\s()]+");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9.-]*\\+?");
    private static final Set<String> UNKNOWN = Set.of("UNLICENSED", "UNKNOWN", "NONE", "NOASSERTION");

    static boolean expression(String declared) {
        List<String> tokens = TOKENS.matcher(declared).results().map(MatchResult::group).toList();
        // This stream is sequential: a grammar consumes tokens in source order.
        State result = tokens.stream().reduce(new State(0, true, true), State::next, (_, right) -> right);
        boolean complete = result.valid() && !result.operand() && result.depth() == 0;
        return tokens.size() > 1 && complete;
    }

    /**
     * One immutable prefix assessment.
     *
     * @param depth   open parentheses
     * @param operand whether the next token must begin an operand
     * @param valid   whether every token so far belongs to the grammar
     */
    private record State(int depth, boolean operand, boolean valid) {

        private State next(String token) {
            return switch (token) {
                case null -> throw new IllegalArgumentException("Missing SPDX expression token");
                case "(" -> this.open();
                case ")" -> this.close();
                case "AND", "OR", "WITH" -> this.operator();
                default -> this.identifier(token);
            };
        }

        private State open() {
            return new State(this.depth + 1, true, this.valid && this.operand);
        }

        private State close() {
            return new State(this.depth - 1, false, this.valid && !this.operand && this.depth > 0);
        }

        private State operator() {
            return new State(this.depth, true, this.valid && !this.operand);
        }

        private State identifier(String token) {
            boolean recognized = IDENTIFIER.matcher(token).matches()
                && !UNKNOWN.contains(token.toUpperCase(Locale.ROOT));
            return new State(this.depth, false, this.valid && this.operand && recognized);
        }
    }
}
