package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import java.io.Serial;

/**
 * Leaves the type arguments of a generic method call to inference where the call is assigned or returned.
 *
 * <p>A call that initializes a variable, is assigned, or is returned receives its type from that target, so
 * written type arguments repeat it. A call used as a receiver or as an argument may need them to pick a
 * type or an overload and is left alone.
 */
@BugPattern(
    summary = "The target type supplies these type arguments; remove them",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessTypeArgumentsAreInferred extends BugChecker implements MethodInvocationTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public Description matchMethodInvocation(MethodInvocationTree invocation, VisitorState state) {
        if (invocation.getTypeArguments().isEmpty()) {
            return Description.NO_MATCH;
        }
        Tree parent = state.getPath().getParentPath().getLeaf();
        boolean targeted = switch (parent) {
            case ReturnTree _ -> true;
            case VariableTree variable -> invocation.equals(variable.getInitializer());
            case AssignmentTree assignment -> invocation.equals(assignment.getExpression());
            case null, default -> false;
        };
        return targeted ? this.describeMatch(invocation) : Description.NO_MATCH;
    }
}
