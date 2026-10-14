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
import java.util.Map;
import java.util.Optional;

/**
 * Reads a value the JVM already exposes as a system property from that property.
 *
 * <p>The home directory, the user name and the temporary directory are named differently in the
 * environment of every operating system and may be absent from it, while the JVM resolves each once and
 * publishes it under a fixed property. Only a literal or constant naming one of those environment
 * variables is reported, since any other name has no property standing in for it.
 */
@BugPattern(
    summary = "Read the system property the JVM resolves rather than the environment variable",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessPropertyOverEnvironment extends BugChecker implements MethodInvocationTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> GETENV = MethodMatchers.staticMethod()
        .onClass("java.lang.System")
        .named("getenv")
        .withParameters("java.lang.String");

    private static final Map<String, String> PROPERTIES = Map.of(
        "HOME", "user.home",
        "USERPROFILE", "user.home",
        "USER", "user.name",
        "USERNAME", "user.name",
        "TEMP", "java.io.tmpdir",
        "TMP", "java.io.tmpdir",
        "TMPDIR", "java.io.tmpdir"
    );

    @Override
    public Description matchMethodInvocation(MethodInvocationTree invocation, VisitorState state) {
        if (!GETENV.matches(invocation, state)) {
            return Description.NO_MATCH;
        }
        return Optional.ofNullable(ASTHelpers.constValue(invocation.getArguments().getFirst(), String.class))
            .map(PROPERTIES::get)
            .map(property -> this.report(invocation, property))
            .orElse(Description.NO_MATCH);
    }

    private Description report(MethodInvocationTree invocation, String property) {
        return this.buildDescription(invocation)
            .setMessage("Read System.getProperty(\"" + property + "\") instead of this environment variable")
            .build();
    }
}
