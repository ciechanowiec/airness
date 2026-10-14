package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.VariableTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.suppliers.Suppliers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import java.io.Serial;
import javax.lang.model.element.ElementKind;

/**
 * Marks the {@code serialVersionUID} of a serializable class with {@link Serial}, so the compiler checks
 * its declaration.
 */
@BugPattern(
    summary = "Annotate serialVersionUID with @java.io.Serial",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessSerialFieldIsAnnotated extends BugChecker implements VariableTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Supplier<Type> SERIALIZABLE = Suppliers.typeFromString("java.io.Serializable");

    @Override
    public Description matchVariable(VariableTree field, VisitorState state) {
        Symbol.VarSymbol symbol = ASTHelpers.getSymbol(field);
        boolean version = symbol.getKind() == ElementKind.FIELD && symbol.getSimpleName()
            .contentEquals("serialVersionUID");
        boolean unannotated = ASTHelpers.isSubtype(symbol.owner.type, SERIALIZABLE.get(state), state)
            && !ASTHelpers.hasAnnotation(symbol, "java.io.Serial", state);
        return version && unannotated ? this.describeMatch(field) : Description.NO_MATCH;
    }
}
