package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.ClassTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.suppliers.Suppliers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Type;
import java.io.Serial;
import java.util.Optional;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;

/**
 * Gives an {@code Externalizable} class the no-argument constructor its deserialization calls.
 *
 * <p>Deserialization of an {@code Externalizable} instance starts with that constructor and fails at run
 * time when it is missing. A class declaring constructors that all take arguments is reported, unless a
 * final field without an initializer would leave such a constructor impossible to write.
 */
@BugPattern(
    summary = "Declare the no-argument constructor that deserializing an Externalizable class calls",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessExternalizableHasNoArgConstructor extends BugChecker implements ClassTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Supplier<Type> EXTERNALIZABLE = Suppliers.typeFromString("java.io.Externalizable");

    @Override
    public Description matchClass(ClassTree declaration, VisitorState state) {
        boolean externalizable = ASTHelpers.getSymbol(declaration).getKind() == ElementKind.CLASS
            && ASTHelpers.isSubtype(
                ASTHelpers.getType(declaration), EXTERNALIZABLE.get(state), state
            );
        if (!externalizable || hasNoArgumentConstructor(declaration) || hasUnassignedFinalField(declaration)) {
            return Description.NO_MATCH;
        }
        return this.describeMatch(declaration);
    }

    private static boolean hasNoArgumentConstructor(ClassTree declaration) {
        return declaration.getMembers().stream()
            .filter(MethodTree.class::isInstance)
            .map(MethodTree.class::cast)
            .filter(method -> ASTHelpers.getSymbol(method).isConstructor())
            .anyMatch(constructor -> constructor.getParameters().isEmpty());
    }

    private static boolean hasUnassignedFinalField(ClassTree declaration) {
        return declaration.getMembers().stream()
            .filter(VariableTree.class::isInstance)
            .map(VariableTree.class::cast)
            .filter(field -> !field.getModifiers().getFlags().contains(Modifier.STATIC))
            .anyMatch(AirnessExternalizableHasNoArgConstructor::unassignedFinal);
    }

    private static boolean unassignedFinal(VariableTree field) {
        return field.getModifiers().getFlags().contains(Modifier.FINAL) && Optional.ofNullable(field.getInitializer())
            .isEmpty();
    }
}
