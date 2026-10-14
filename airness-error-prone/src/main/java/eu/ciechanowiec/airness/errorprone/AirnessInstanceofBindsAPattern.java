package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.InstanceOfTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import java.io.Serial;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Binds the tested value in the {@code instanceof} that tests it rather than casting it again afterwards.
 *
 * <p>A test of a variable or a field followed by a cast of the same value to the same type, inside the
 * branch the test guards or later in the same conjunction, is reported.
 */
@BugPattern(
    summary = "Bind the value with an instanceof pattern rather than casting it after the test",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessInstanceofBindsAPattern extends BugChecker implements InstanceOfTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public Description matchInstanceOf(InstanceOfTree test, VisitorState state) {
        Symbol tested = ASTHelpers.getSymbol(test.getExpression());
        if (Optional.ofNullable(test.getPattern()).isPresent() || !(tested instanceof Symbol.VarSymbol)) {
            return Description.NO_MATCH;
        }
        Type type = state.getTypes().erasure(ASTHelpers.getType(test.getType()));
        boolean cast = guarded(state.getPath()).stream().anyMatch(scope -> castsAgain(scope, tested, type, state));
        return cast ? this.describeMatch(test) : Description.NO_MATCH;
    }

    private static List<Tree> guarded(TreePath path) {
        return Optional.ofNullable(path.getParentPath())
            .map(parentPath -> guardedBy(parentPath, path.getLeaf()))
            .orElse(List.of());
    }

    private static List<Tree> guardedBy(TreePath parentPath, Tree child) {
        return switch (parentPath.getLeaf()) {
            case IfTree branch when branch.getCondition().equals(child) -> List.of(branch.getThenStatement());
            case ConditionalExpressionTree choice when choice.getCondition().equals(child) ->
                List.of(choice.getTrueExpression());
            case BinaryTree conjunction when leftOfConjunction(conjunction, child) ->
                Stream.concat(Stream.of(conjunction.getRightOperand()), guarded(parentPath).stream()).toList();
            case Tree enclosing when transparent(enclosing) -> guarded(parentPath);
            case null, default -> List.of();
        };
    }

    private static boolean transparent(Tree enclosing) {
        return enclosing instanceof ParenthesizedTree || enclosing instanceof BinaryTree;
    }

    private static boolean leftOfConjunction(Tree parent, Tree child) {
        return parent instanceof BinaryTree conjunction
            && conjunction.getKind() == Tree.Kind.CONDITIONAL_AND
            && conjunction.getLeftOperand().equals(child);
    }

    private static boolean castsAgain(Tree scope, Symbol tested, Type type, VisitorState state) {
        Boolean found = new TreeScanner<@Nullable Boolean, @Nullable Void>() {

            @Override
            public Boolean visitTypeCast(TypeCastTree cast, @Nullable Void unused) {
                boolean same = Objects.equals(tested, ASTHelpers.getSymbol(cast.getExpression()))
                    && Optional.ofNullable(ASTHelpers.getType(cast.getType()))
                        .filter(target -> ASTHelpers.isSameType(state.getTypes().erasure(target), type, state))
                        .isPresent();
                return same || Boolean.TRUE.equals(super.visitTypeCast(cast, null));
            }

            @Override
            public Boolean reduce(@Nullable Boolean left, @Nullable Boolean right) {
                return Boolean.TRUE.equals(left) || Boolean.TRUE.equals(right);
            }
        }.scan(scope, null);
        return Boolean.TRUE.equals(found);
    }
}
