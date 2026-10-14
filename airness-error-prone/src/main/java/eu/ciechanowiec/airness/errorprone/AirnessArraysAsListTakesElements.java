package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewArrayTree;
import java.io.Serial;
import java.util.Optional;

/**
 * Passes the elements to {@code Arrays.asList} rather than an array created only to hold them.
 */
@BugPattern(
    summary = "Pass the elements to Arrays.asList directly rather than wrapping them in an array",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessArraysAsListTakesElements extends BugChecker implements MethodInvocationTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> AS_LIST = MethodMatchers.staticMethod()
        .onClass("java.util.Arrays")
        .named("asList");

    private static boolean listsElements(ExpressionTree argument) {
        return argument instanceof NewArrayTree array && array.getDimensions().isEmpty() && Optional.ofNullable(
            array.getInitializers()
        ).filter(initializers -> !initializers.isEmpty()).isPresent();
    }

    @Override
    public Description matchMethodInvocation(MethodInvocationTree invocation, VisitorState state) {
        boolean wrapped = invocation.getArguments().size() == 1 && listsElements(invocation.getArguments().getFirst());
        return wrapped && AS_LIST.matches(invocation, state) ? this.describeMatch(invocation) : Description.NO_MATCH;
    }
}
