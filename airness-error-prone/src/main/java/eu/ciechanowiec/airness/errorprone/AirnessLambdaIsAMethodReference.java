package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.LambdaExpressionTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;
import java.io.Serial;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.jspecify.annotations.Nullable;

/**
 * Writes a lambda that only constructs, tests, casts or null-checks its parameters as a method reference.
 *
 * <p>The forms read here are a constructor taking the parameters in order, an {@code instanceof} of a type
 * without type arguments, a cast to such a type, and a comparison with null. Lambdas that call a method on
 * their parameters are read by PMD's {@code LambdaCanBeMethodReference}.
 */
@BugPattern(
    summary = "Write the lambda as the method reference it spells out",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessLambdaIsAMethodReference extends BugChecker implements LambdaExpressionTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public Description matchLambdaExpression(LambdaExpressionTree lambda, VisitorState state) {
        List<Symbol> parameters = lambda.getParameters().stream().map(ASTHelpers::getSymbol).map(Symbol.class::cast)
            .toList();
        return replaceable(lambda.getBody(), parameters) ? this.describeMatch(lambda) : Description.NO_MATCH;
    }

    private static boolean replaceable(@Nullable Tree body, List<Symbol> parameters) {
        return switch (body) {
            case ParenthesizedTree wrapped -> replaceable(wrapped.getExpression(), parameters);
            case NewClassTree creation -> plainCreation(creation, parameters);
            case InstanceOfTree test -> plainTest(test, parameters);
            case TypeCastTree cast -> plainCast(cast, parameters);
            case BinaryTree comparison -> comparesWithNull(comparison, parameters);
            case null, default -> false;
        };
    }

    private static boolean plainCreation(NewClassTree creation, List<Symbol> parameters) {
        return anonymous(creation).isEmpty() && passesInOrder(creation, parameters);
    }

    private static boolean plainTest(InstanceOfTree test, List<Symbol> parameters) {
        return Optional.ofNullable(test.getPattern()).isEmpty()
            && plainType(test.getType()) && isOnly(test.getExpression(), parameters);
    }

    private static boolean plainCast(TypeCastTree cast, List<Symbol> parameters) {
        return plainType(cast.getType()) && isOnly(cast.getExpression(), parameters);
    }

    private static Optional<ClassTree> anonymous(NewClassTree creation) {
        return Optional.ofNullable(creation.getClassBody());
    }

    private static boolean passesInOrder(NewClassTree creation, List<Symbol> parameters) {
        List<? extends ExpressionTree> arguments = creation.getArguments();
        return arguments.size() == parameters.size() && IntStream.range(0, arguments.size())
            .allMatch(index -> refersTo(arguments.get(index), parameters.get(index)));
    }

    private static boolean comparesWithNull(BinaryTree comparison, List<Symbol> parameters) {
        boolean equality = comparison.getKind() == Tree.Kind.EQUAL_TO || comparison.getKind() == Tree.Kind.NOT_EQUAL_TO;
        ExpressionTree left = comparison.getLeftOperand();
        ExpressionTree right = comparison.getRightOperand();
        boolean leftNull = left.getKind() == Tree.Kind.NULL_LITERAL;
        return equality && (leftNull ? isOnly(right, parameters) : right.getKind() == Tree.Kind.NULL_LITERAL
            && isOnly(left, parameters));
    }

    private static boolean isOnly(ExpressionTree expression, List<Symbol> parameters) {
        return parameters.size() == 1 && refersTo(expression, parameters.getFirst());
    }

    private static boolean refersTo(ExpressionTree expression, Symbol parameter) {
        return expression instanceof IdentifierTree && parameter.equals(ASTHelpers.getSymbol(expression));
    }

    private static boolean plainType(Tree type) {
        return !(type instanceof ParameterizedTypeTree) && !(type instanceof VariableTree);
    }
}
