package eu.ciechanowiec.airness.governance;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Asks a for loop that steps its counter by one to bound it with an ordering rather than an inequality.
 *
 * <p>The condition is read when it compares a counter the loop initialises with {@code !=}, and the update
 * clause increments or decrements by one. A larger step can pass over the bound either way, so it is
 * left alone, as is a condition about a variable the loop does not initialise.
 */
public final class LoopBoundOrderingCheck extends AbstractCheck {

    private static final Set<Integer> STEPS = Set.of(
        TokenTypes.POST_INC, TokenTypes.POST_DEC, TokenTypes.INC, TokenTypes.DEC
    );

    @Override
    public int[] getDefaultTokens() {
        return new int[] {TokenTypes.LITERAL_FOR};
    }

    @Override
    public int[] getAcceptableTokens() {
        return this.getDefaultTokens();
    }

    @Override
    public int[] getRequiredTokens() {
        return new int[] {};
    }

    @Override
    public void visitToken(DetailAST loop) {
        Set<String> counters = LoopCounters.initialised(loop);
        boolean comparesCounter = inequality(loop)
            .flatMap(LoopBoundOrderingCheck::operands)
            .anyMatch(operand -> counters.contains(operand.getText()));
        if (comparesCounter && stepsByOne(loop)) {
            this.log(loop, "loop.bound.inequality");
        }
    }

    private static Stream<DetailAST> inequality(DetailAST loop) {
        return Optional.ofNullable(loop.findFirstToken(TokenTypes.FOR_CONDITION))
            .map(condition -> condition.findFirstToken(TokenTypes.EXPR))
            .map(DetailAST::getFirstChild)
            .filter(comparison -> comparison.getType() == TokenTypes.NOT_EQUAL)
            .stream();
    }

    private static Stream<DetailAST> operands(DetailAST comparison) {
        return Stream.of(comparison.getFirstChild(), comparison.getLastChild())
            .filter(operand -> operand.getType() == TokenTypes.IDENT);
    }

    private static boolean stepsByOne(DetailAST loop) {
        return Optional.ofNullable(loop.findFirstToken(TokenTypes.FOR_ITERATOR))
            .map(update -> update.findFirstToken(TokenTypes.ELIST))
            .stream()
            .flatMap(LoopCounters::children)
            .filter(expression -> expression.getType() == TokenTypes.EXPR)
            .map(DetailAST::getFirstChild)
            .anyMatch(step -> STEPS.contains(step.getType()));
    }
}
