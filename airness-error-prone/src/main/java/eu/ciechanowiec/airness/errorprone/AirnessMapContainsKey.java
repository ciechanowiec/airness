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
import java.io.Serial;
import java.util.Optional;

/**
 * Asks a map whether it holds a key rather than asking its key set.
 */
@BugPattern(
    summary = "Ask the map containsKey(key) rather than keySet().contains(key)",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessMapContainsKey extends BugChecker implements MethodInvocationTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> CONTAINS = MethodMatchers.instanceMethod()
        .onDescendantOf("java.util.Set")
        .named("contains").withParameters("java.lang.Object");

    private static final Matcher<ExpressionTree> KEY_SET = MethodMatchers.instanceMethod()
        .onDescendantOf("java.util.Map")
        .named("keySet").withNoParameters();

    @Override
    public Description matchMethodInvocation(MethodInvocationTree invocation, VisitorState state) {
        return Optional.ofNullable(ASTHelpers.getReceiver(invocation))
            .filter(receiver -> CONTAINS.matches(invocation, state) && KEY_SET.matches(receiver, state))
            .map(_ -> this.describeMatch(invocation))
            .orElse(Description.NO_MATCH);
    }
}
