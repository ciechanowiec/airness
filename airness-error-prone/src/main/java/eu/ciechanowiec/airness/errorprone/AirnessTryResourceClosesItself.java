package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.TryTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.TryTree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol;
import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Leaves closing a try-with-resources resource to the statement that declared it.
 *
 * <p>The statement closes every resource when its block ends, so a {@code close()} written inside the
 * block closes it twice, and anything the block does after that call meets a closed resource. A call inside
 * a lambda or a class declared in the block runs later and is left alone.
 */
@BugPattern(
    summary = "The try-with-resources statement closes this resource; remove the explicit close()",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessTryResourceClosesItself extends BugChecker implements TryTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public Description matchTry(TryTree statement, VisitorState state) {
        Set<Symbol> resources = statement.getResources().stream()
            .flatMap(resource -> Optional.ofNullable(ASTHelpers.getSymbol(resource)).stream())
            .collect(Collectors.toUnmodifiableSet());
        List<MethodInvocationTree> closes = new ArrayList<>();
        new Closes(resources, closes).scan(statement.getBlock(), null);
        closes.forEach(call -> state.reportMatch(this.describeMatch(call)));
        return Description.NO_MATCH;
    }

    private static final class Closes extends TreeScanner<@Nullable Boolean, @Nullable Void> {

        private final Set<Symbol> resources;
        private final List<MethodInvocationTree> found;

        Closes(Set<Symbol> resources, List<MethodInvocationTree> found) {
            this.resources = resources;
            this.found = found;
        }

        @Override
        public @Nullable Boolean visitMethodInvocation(MethodInvocationTree call, @Nullable Void unused) {
            boolean closes = ASTHelpers.getSymbol(call).getSimpleName().contentEquals("close")
                && call.getArguments().isEmpty()
                && Optional.ofNullable(ASTHelpers.getReceiver(call))
                    .map(ASTHelpers::getSymbol)
                    .filter(this.resources::contains)
                    .isPresent();
            if (closes) {
                this.found.add(call);
            }
            return super.visitMethodInvocation(call, null);
        }

        @Override
        public Boolean visitLambdaExpression(LambdaExpressionTree lambda, @Nullable Void unused) {
            return Boolean.FALSE;
        }

        @Override
        public Boolean visitClass(ClassTree declaration, @Nullable Void unused) {
            return Boolean.FALSE;
        }
    }
}
