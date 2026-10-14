package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.Matchers;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.suppliers.Suppliers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.tools.javac.code.Type;
import java.io.Serial;
import java.util.List;
import java.util.Optional;

/**
 * Writes a line break in a format string as {@code %n}.
 *
 * <p>A format string is read by the formatter, which turns {@code %n} into the platform line separator,
 * while a written {@code \n} is always a line feed. A literal format string whose source spells a
 * {@code \n} escape is reported. A text block writes its line breaks as lines and is left alone.
 */
@BugPattern(
    summary = "Write a line break in a format string as %n",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessFormatNewlineIsPortable extends BugChecker implements MethodInvocationTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final List<Supplier<Type>> FORMAT_AND_ARGUMENTS = List.of(
        Suppliers.STRING_TYPE, Suppliers.arrayOf(Suppliers.OBJECT_TYPE)
    );

    private static final Matcher<ExpressionTree> FIRST_ARGUMENT = Matchers.anyOf(
        MethodMatchers.staticMethod().onClass("java.lang.String").named("format").withParametersOfType(
            FORMAT_AND_ARGUMENTS
        ),
        MethodMatchers.instanceMethod().onDescendantOf("java.io.PrintStream").named("printf").withParametersOfType(
            FORMAT_AND_ARGUMENTS
        ),
        MethodMatchers.instanceMethod().onDescendantOf("java.io.PrintWriter").named("printf").withParametersOfType(
            FORMAT_AND_ARGUMENTS
        ),
        MethodMatchers.instanceMethod().onExactClass("java.util.Formatter").named("format").withParametersOfType(
            FORMAT_AND_ARGUMENTS
        )
    );

    private static final Matcher<ExpressionTree> RECEIVER = MethodMatchers.instanceMethod()
        .onExactClass("java.lang.String")
        .named("formatted");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree invocation, VisitorState state) {
        Optional<ExpressionTree> format = Optional.empty();
        if (FIRST_ARGUMENT.matches(invocation, state)) {
            format = Optional.of(invocation.getArguments().getFirst());
        } else if (RECEIVER.matches(invocation, state)) {
            format = Optional.ofNullable(ASTHelpers.getReceiver(invocation));
        }
        return format.filter(LiteralTree.class::isInstance)
            .map(state::getSourceForNode)
            .filter(source -> !source.startsWith("\"\"\"") && source.contains("\\n"))
            .map(_ -> this.describeMatch(invocation))
            .orElse(Description.NO_MATCH);
    }
}
