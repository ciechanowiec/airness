package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.TypeCastTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.TypeCastTree;
import java.io.Serial;
import java.util.Optional;
import javax.lang.model.type.TypeKind;

/**
 * Asks a list made by {@code Arrays.asList} for an array of its element type rather than casting the
 * untyped array it returns.
 *
 * <p>The array {@code toArray()} returns is an {@link Object} array whatever the collection holds, so a cast
 * to any narrower array type fails when it runs.
 */
@BugPattern(
    summary = "Pass a typed array to toArray rather than casting the Object[] it returns",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessToArrayIsTyped extends BugChecker implements TypeCastTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> UNTYPED = MethodMatchers.instanceMethod()
        .onDescendantOf("java.util.Collection").named("toArray").withNoParameters();

    private static final Matcher<ExpressionTree> AS_LIST = MethodMatchers.staticMethod()
        .onClass("java.util.Arrays").named("asList");

    @Override
    public Description matchTypeCast(TypeCastTree cast, VisitorState state) {
        ExpressionTree converted = ASTHelpers.stripParentheses(cast.getExpression());
        boolean array = Optional.ofNullable(ASTHelpers.getType(cast.getType()))
            .filter(target -> target.getKind() == TypeKind.ARRAY).isPresent();
        boolean fromList = UNTYPED.matches(converted, state) && Optional.ofNullable(ASTHelpers.getReceiver(converted))
            .filter(receiver -> AS_LIST.matches(receiver, state)).isPresent();
        return array && fromList ? this.describeMatch(cast) : Description.NO_MATCH;
    }
}
