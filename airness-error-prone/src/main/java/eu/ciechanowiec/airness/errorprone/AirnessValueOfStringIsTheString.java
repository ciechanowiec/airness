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
 * Uses a string directly rather than passing it through {@link String#valueOf(Object)}.
 *
 * <p>A call used as the receiver of another call is left alone, since there the conversion also states
 * how a null receiver is answered.
 */
@BugPattern(
    summary = "The value is already a String; use it rather than String.valueOf(value)",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessValueOfStringIsTheString extends BugChecker implements MethodInvocationTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> VALUE_OF = MethodMatchers.staticMethod()
        .onClass("java.lang.String")
        .named("valueOf");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree invocation, VisitorState state) {
        boolean ofString = invocation.getArguments().size() == 1 && ASTHelpers.isSameType(
            ASTHelpers.getType(invocation.getArguments().getFirst()), state.getSymtab().stringType, state
        );
        boolean receiver = state.getPath().getParentPath().getLeaf() instanceof MemberSelectTree;
        return ofString && !receiver && VALUE_OF.matches(invocation, state)
            ? this.describeMatch(invocation) : Description.NO_MATCH;
    }
}
