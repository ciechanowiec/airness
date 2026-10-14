package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.NewClassTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.suppliers.Supplier;
import com.google.errorprone.suppliers.Suppliers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.NewClassTree;
import com.sun.tools.javac.code.Type;
import java.io.Serial;
import java.util.Optional;

/**
 * Opens a {@code FileInputStream} from what names the file rather than from a {@code File} made for it.
 */
@BugPattern(
    summary = "Pass the path to FileInputStream directly rather than wrapping it in a new File",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessFileStreamTakesThePath extends BugChecker implements NewClassTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Supplier<Type> FILE_INPUT_STREAM = Suppliers.typeFromString("java.io.FileInputStream");

    private static final Supplier<Type> FILE = Suppliers.typeFromString("java.io.File");

    @Override
    public Description matchNewClass(NewClassTree creation, VisitorState state) {
        boolean stream = creation.getArguments().size() == 1 && creates(creation, FILE_INPUT_STREAM, state);
        if (!stream) {
            return Description.NO_MATCH;
        }
        ExpressionTree argument = creation.getArguments().getFirst();
        return Optional.of(argument)
            .filter(NewClassTree.class::isInstance)
            .map(NewClassTree.class::cast)
            .filter(file -> file.getArguments().size() == 1 && creates(file, FILE, state))
            .map(_ -> this.describeMatch(creation))
            .orElse(Description.NO_MATCH);
    }

    private static boolean creates(NewClassTree creation, Supplier<Type> type, VisitorState state) {
        return ASTHelpers.isSameType(ASTHelpers.getType(creation), type.get(state), state);
    }
}
