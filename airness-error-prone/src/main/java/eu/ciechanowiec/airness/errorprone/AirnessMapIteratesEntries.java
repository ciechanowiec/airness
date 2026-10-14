package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.EnhancedForLoopTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreeScanner;
import com.sun.tools.javac.code.Symbol;
import java.io.Serial;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Iterates the entries of a map whose loop over the keys looks each value up again.
 *
 * <p>The loop is reported when its body calls {@code get} on the same map with the loop variable, reassigns
 * neither, and captures the key in no lambda or local class, since only then does the entry carry the same
 * key and value the lookup would.
 */
@BugPattern(
    summary = "Iterate entrySet() rather than looking up every key of keySet() again",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessMapIteratesEntries extends BugChecker implements EnhancedForLoopTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> KEY_SET = MethodMatchers.instanceMethod()
        .onDescendantOf("java.util.Map").named("keySet").withNoParameters();

    private static final Matcher<ExpressionTree> GET = MethodMatchers.instanceMethod()
        .onDescendantOf("java.util.Map").named("get");

    private static final Usage NONE = new Usage(false, false);

    @Override
    public Description matchEnhancedForLoop(EnhancedForLoopTree loop, VisitorState state) {
        if (!KEY_SET.matches(loop.getExpression(), state)) {
            return Description.NO_MATCH;
        }
        Symbol key = ASTHelpers.getSymbol(loop.getVariable());
        return Optional.ofNullable(ASTHelpers.getReceiver(loop.getExpression()))
            .map(ASTHelpers::getSymbol)
            .filter(Symbol.VarSymbol.class::isInstance)
            .map(map -> Optional.ofNullable(new Scanner(map, key, state).scan(loop.getStatement(), null)).orElse(NONE))
            .filter(usage -> usage.looksUp() && !usage.disqualified())
            .map(_ -> this.describeMatch(loop))
            .orElse(Description.NO_MATCH);
    }

    private record Usage(boolean looksUp, boolean disqualified) {

        Usage with(Usage other) {
            return new Usage(this.looksUp || other.looksUp, this.disqualified || other.disqualified);
        }
    }

    private static final class Scanner extends TreeScanner<@Nullable Usage, @Nullable Void> {

        private final Symbol map;
        private final Symbol key;
        private final VisitorState state;

        Scanner(Symbol map, Symbol key, VisitorState state) {
            this.map = map;
            this.key = key;
            this.state = state;
        }

        @Override
        public Usage visitMethodInvocation(MethodInvocationTree call, @Nullable Void unused) {
            boolean lookup = GET.matches(call, this.state)
                && Optional.ofNullable(ASTHelpers.getReceiver(call)).map(ASTHelpers::getSymbol)
                    .filter(this.map::equals).isPresent()
                && Objects.equals(this.key, ASTHelpers.getSymbol(call.getArguments().getFirst()));
            return this.reduce(new Usage(lookup, false), super.visitMethodInvocation(call, null));
        }

        @Override
        public Usage visitAssignment(AssignmentTree assignment, @Nullable Void unused) {
            Symbol target = ASTHelpers.getSymbol(assignment.getVariable());
            boolean reassigns = Objects.equals(this.map, target) || Objects.equals(this.key, target);
            return this.reduce(new Usage(false, reassigns), super.visitAssignment(assignment, null));
        }

        @Override
        public Usage visitLambdaExpression(LambdaExpressionTree lambda, @Nullable Void unused) {
            return new Usage(false, true);
        }

        @Override
        public Usage visitClass(ClassTree declaration, @Nullable Void unused) {
            return new Usage(false, true);
        }

        @Override
        public Usage reduce(@Nullable Usage first, @Nullable Usage second) {
            return Optional.ofNullable(first).orElse(NONE).with(Optional.ofNullable(second).orElse(NONE));
        }
    }
}
