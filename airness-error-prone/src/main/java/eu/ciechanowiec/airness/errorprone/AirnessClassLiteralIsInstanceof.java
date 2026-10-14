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
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import java.io.Serial;

/**
 * Tests a type the source names with {@code instanceof} rather than {@link Class#isInstance(Object)}.
 */
@BugPattern(
    summary = "Write instanceof for a type named in the source rather than calling isInstance on its literal",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessClassLiteralIsInstanceof extends BugChecker implements MethodInvocationTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> IS_INSTANCE = MethodMatchers.instanceMethod()
        .onExactClass("java.lang.Class")
        .named("isInstance");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree invocation, VisitorState state) {
        boolean literal = ASTHelpers.getReceiver(invocation) instanceof MemberSelectTree select
            && select.getIdentifier().contentEquals("class");
        return literal && IS_INSTANCE.matches(invocation, state) ? this.describeMatch(invocation)
            : Description.NO_MATCH;
    }
}
