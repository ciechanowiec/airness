package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Type;
import java.io.Serial;

/**
 * Compares two atomic primitives by the values they hold.
 *
 * <p>{@code AtomicInteger}, {@code AtomicLong} and {@code AtomicBoolean} inherit identity equality, so
 * comparing two of them with {@code equals} asks whether they are the same holder. Only a call whose
 * argument has the receiver's own atomic type is reported.
 */
@BugPattern(
    summary = "Compare the values of two atomic primitives with get() rather than equals()",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessAtomicEqualsUsesGet extends BugChecker implements MethodInvocationTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String[] ATOMICS = {
        "java.util.concurrent.atomic.AtomicInteger", "java.util.concurrent.atomic.AtomicLong",
        "java.util.concurrent.atomic.AtomicBoolean"
    };

    private static final Matcher<ExpressionTree> EQUALS = MethodMatchers.instanceMethod()
        .onExactClassAny(ATOMICS)
        .named("equals");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree invocation, VisitorState state) {
        if (!EQUALS.matches(invocation, state)) {
            return Description.NO_MATCH;
        }
        Type receiver = ASTHelpers.getReceiverType(invocation);
        Type argument = ASTHelpers.getType(invocation.getArguments().getFirst());
        return ASTHelpers.isSameType(receiver, argument, state) ? this.describeMatch(invocation) : Description.NO_MATCH;
    }
}
