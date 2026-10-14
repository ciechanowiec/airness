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
import java.util.Optional;

/**
 * Converts or compares a primitive through its wrapper's static method rather than a boxed instance.
 *
 * <p>{@link Integer#valueOf(int)} followed by {@code toString()} boxes only to call one method, while
 * {@link Integer#toString(int)} and {@link Integer#compare(int, int)} answer the same without the box.
 */
@BugPattern(
    summary = "Call the wrapper's static toString or compare rather than boxing the primitive first",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessWrapperIsNotBoxedForConversion extends BugChecker implements MethodInvocationTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String[] WRAPPERS = {
        "java.lang.Boolean", "java.lang.Byte", "java.lang.Character", "java.lang.Short",
        "java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double"
    };

    private static final Matcher<ExpressionTree> BOXING = MethodMatchers.staticMethod()
        .onClassAny(WRAPPERS)
        .named("valueOf");

    private static final Matcher<ExpressionTree> CONVERSION = MethodMatchers.instanceMethod()
        .onExactClassAny(WRAPPERS)
        .namedAnyOf("toString", "compareTo");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree invocation, VisitorState state) {
        if (!CONVERSION.matches(invocation, state)) {
            return Description.NO_MATCH;
        }
        return Optional.ofNullable(ASTHelpers.getReceiver(invocation))
            .filter(receiver -> BOXING.matches(receiver, state))
            .map(MethodInvocationTree.class::cast)
            .filter(boxing -> boxing.getArguments().size() == 1)
            .map(boxing -> ASTHelpers.getType(boxing.getArguments().getFirst()))
            .filter(Type::isPrimitive)
            .map(_ -> this.describeMatch(invocation))
            .orElse(Description.NO_MATCH);
    }
}
