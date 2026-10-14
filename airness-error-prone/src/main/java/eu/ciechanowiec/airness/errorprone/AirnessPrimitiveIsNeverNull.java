package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.VariableTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import java.io.Serial;
import java.util.stream.Stream;

/**
 * Removes a JSR 305 nullness annotation from a primitive, which can never hold null.
 */
@BugPattern(
    summary = "A primitive can never be null; remove the nullness annotation",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessPrimitiveIsNeverNull extends BugChecker implements MethodTreeMatcher, VariableTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public Description matchMethod(MethodTree method, VisitorState state) {
        Symbol.MethodSymbol symbol = ASTHelpers.getSymbol(method);
        return this.match(method, symbol, symbol.getReturnType(), state);
    }

    @Override
    public Description matchVariable(VariableTree variable, VisitorState state) {
        Symbol.VarSymbol symbol = ASTHelpers.getSymbol(variable);
        return this.match(variable, symbol, symbol.asType(), state);
    }

    private Description match(Tree declaration, Symbol symbol, Type type, VisitorState state) {
        boolean annotated = Stream.of("javax.annotation.Nullable", "javax.annotation.CheckForNull")
            .anyMatch(annotation -> ASTHelpers.hasAnnotation(symbol, annotation, state));
        return annotated && type.isPrimitive() ? this.describeMatch(declaration) : Description.NO_MATCH;
    }
}
