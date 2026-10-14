package eu.ciechanowiec.airness.governance;

import com.puppycrawl.tools.checkstyle.api.AbstractCheck;
import com.puppycrawl.tools.checkstyle.api.DetailAST;
import com.puppycrawl.tools.checkstyle.api.TokenTypes;
import java.util.Optional;
import java.util.Set;

/**
 * Asks a for loop that declares a counter and steps it as the last statement of its body to step it in
 * the update clause instead.
 *
 * <p>Only a loop with an empty update clause is read, and only an increment or a decrement of a variable
 * the loop declares itself. A counter declared before the loop, or stepped anywhere but last, is left
 * alone, since moving it would change what the loop does.
 */
public final class LoopStepInHeaderCheck extends AbstractCheck {

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
        Optional<DetailAST> update = Optional.ofNullable(loop.findFirstToken(TokenTypes.FOR_ITERATOR));
        boolean emptyUpdate = update.map(DetailAST::getChildCount).filter(count -> count == 0).isPresent();
        if (emptyUpdate && lastStep(loop).filter(LoopCounters.declared(loop)::contains).isPresent()) {
            this.log(loop, "loop.step.in.body");
        }
    }

    private static Optional<String> lastStep(DetailAST loop) {
        return Optional.ofNullable(loop.findFirstToken(TokenTypes.SLIST))
            .map(body -> body.findFirstToken(TokenTypes.RCURLY))
            .map(DetailAST::getPreviousSibling)
            .filter(semicolon -> semicolon.getType() == TokenTypes.SEMI)
            .map(DetailAST::getPreviousSibling)
            .filter(statement -> statement.getType() == TokenTypes.EXPR)
            .map(DetailAST::getFirstChild)
            .filter(step -> STEPS.contains(step.getType()))
            .map(DetailAST::getFirstChild)
            .filter(target -> target.getType() == TokenTypes.IDENT)
            .map(DetailAST::getText);
    }
}
