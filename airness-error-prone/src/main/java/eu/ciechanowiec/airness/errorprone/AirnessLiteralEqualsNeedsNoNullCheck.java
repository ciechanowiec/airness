package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.BinaryTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import java.io.Serial;
import java.util.Optional;

/**
 * Drops a null check that a literal's {@code equals} already performs.
 *
 * <p>{@code "x".equals(value)} answers false for null, so {@code value != null && "x".equals(value)} checks
 * the same thing twice. The check must be the conjunct immediately before the comparison.
 */
@BugPattern(
    summary = "A string literal's equals already rejects null; drop the null check before it",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessLiteralEqualsNeedsNoNullCheck extends BugChecker implements BinaryTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public Description matchBinary(BinaryTree conjunction, VisitorState state) {
        boolean and = conjunction.getKind() == Tree.Kind.CONDITIONAL_AND;
        Optional<Symbol> compared = comparedSymbol(conjunction.getRightOperand()).filter(_ -> and);
        Optional<Symbol> checked = nullChecked(lastConjunct(ASTHelpers.stripParentheses(conjunction.getLeftOperand())));
        return compared.isPresent() && compared.equals(checked) ? this.describeMatch(conjunction)
            : Description.NO_MATCH;
    }

    private static ExpressionTree lastConjunct(ExpressionTree expression) {
        return expression instanceof BinaryTree chain && chain.getKind() == Tree.Kind.CONDITIONAL_AND
            ? ASTHelpers.stripParentheses(chain.getRightOperand()) : expression;
    }

    private static Optional<Symbol> comparedSymbol(ExpressionTree expression) {
        return Optional.of(ASTHelpers.stripParentheses(expression))
            .filter(MethodInvocationTree.class::isInstance)
            .map(MethodInvocationTree.class::cast)
            .filter(call -> ASTHelpers.getSymbol(call).getSimpleName().contentEquals("equals"))
            .filter(call -> call.getArguments().size() == 1)
            .filter(AirnessLiteralEqualsNeedsNoNullCheck::onStringLiteral)
            .map(call -> call.getArguments().getFirst())
            .filter(IdentifierTree.class::isInstance)
            .map(ASTHelpers::getSymbol);
    }

    private static boolean onStringLiteral(MethodInvocationTree call) {
        return ASTHelpers.getReceiver(call) instanceof LiteralTree literal && literal.getKind()
            == Tree.Kind.STRING_LITERAL;
    }

    private static Optional<Symbol> nullChecked(ExpressionTree expression) {
        return Optional.of(expression)
            .filter(BinaryTree.class::isInstance)
            .map(BinaryTree.class::cast)
            .filter(check -> check.getKind() == Tree.Kind.NOT_EQUAL_TO)
            .flatMap(AirnessLiteralEqualsNeedsNoNullCheck::comparedWithNull)
            .filter(IdentifierTree.class::isInstance)
            .map(ASTHelpers::getSymbol);
    }

    private static Optional<ExpressionTree> comparedWithNull(BinaryTree check) {
        if (isNull(check.getLeftOperand())) {
            return Optional.of(check.getRightOperand());
        }
        return isNull(check.getRightOperand()) ? Optional.of(check.getLeftOperand()) : Optional.empty();
    }

    private static boolean isNull(ExpressionTree operand) {
        return operand.getKind() == Tree.Kind.NULL_LITERAL;
    }
}
