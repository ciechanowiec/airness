package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.NewClassTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.suppliers.Suppliers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.NewClassTree;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.code.Types;
import java.io.Serial;

/**
 * Builds a {@code BigDecimal} from a number through {@code BigDecimal.valueOf}.
 *
 * <p>The constructor taking a double keeps the binary approximation of the value, so {@code 0.1} becomes
 * a fifty-digit fraction. A single argument of any primitive or boxed primitive type is reported, because
 * whatever its declared type it reaches the constructor the same way a double does.
 */
@BugPattern(
    summary = "Build a BigDecimal from a number with BigDecimal.valueOf rather than its constructor",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessBigDecimalFromValueOf extends BugChecker implements NewClassTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Supplier<Type> BIG_DECIMAL = Suppliers.typeFromString("java.math.BigDecimal");

    @Override
    public Description matchNewClass(NewClassTree creation, VisitorState state) {
        Type created = ASTHelpers.getType(creation.getIdentifier());
        boolean decimal = ASTHelpers.isSameType(created, BIG_DECIMAL.get(state), state);
        if (!decimal || creation.getArguments().size() != 1) {
            return Description.NO_MATCH;
        }
        Type argument = Types.instance(state.context).unboxedTypeOrType(
            ASTHelpers.getType(creation.getArguments().getFirst())
        );
        return argument.isPrimitive() ? this.describeMatch(creation) : Description.NO_MATCH;
    }
}
