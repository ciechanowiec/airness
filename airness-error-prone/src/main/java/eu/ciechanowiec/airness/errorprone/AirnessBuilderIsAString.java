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
import com.sun.source.tree.NewClassTree;
import java.io.Serial;
import java.util.Optional;

/**
 * Writes a {@link StringBuilder} that is created, appended to and read in one expression as a string
 * concatenation.
 *
 * <p>The compiler already turns a concatenation into the fastest builder it can, so a builder that never
 * outlives the expression only spells that out by hand.
 */
@BugPattern(
    summary = "Concatenate the strings rather than appending them to a builder read in the same expression",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessBuilderIsAString extends BugChecker implements MethodInvocationTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String BUILDER = "java.lang.StringBuilder";

    private static final Matcher<ExpressionTree> READ = MethodMatchers.instanceMethod()
        .onExactClass(BUILDER)
        .named("toString").withNoParameters();

    private static final Matcher<ExpressionTree> APPEND = MethodMatchers.instanceMethod()
        .onExactClass(BUILDER)
        .named("append");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree invocation, VisitorState state) {
        if (!READ.matches(invocation, state)) {
            return Description.NO_MATCH;
        }
        return appendedToFreshBuilder(invocation, state, 0) ? this.describeMatch(invocation) : Description.NO_MATCH;
    }

    private static boolean appendedToFreshBuilder(ExpressionTree call, VisitorState state, int appends) {
        return Optional.ofNullable(ASTHelpers.getReceiver(call))
            .filter(link -> continuesToFreshBuilder(link, state, appends))
            .isPresent();
    }

    private static boolean continuesToFreshBuilder(ExpressionTree link, VisitorState state, int appends) {
        return APPEND.matches(link, state)
            ? appendedToFreshBuilder(link, state, appends + 1) : freshBuilder(link, appends);
    }

    private static boolean freshBuilder(ExpressionTree link, int appends) {
        return appends > 0 && link instanceof NewClassTree creation && Optional.ofNullable(creation.getClassBody())
            .isEmpty();
    }
}
