package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MethodTree;
import com.sun.tools.javac.code.Symbol;
import java.io.Serial;
import java.util.regex.Pattern;
import javax.lang.model.element.ElementKind;

/**
 * Names a method of a class in camel case, without underscores or dollar signs.
 *
 * <p>A method that overrides another keeps the name it overrides, an interface method and a name that
 * starts with an underscore are left alone, and so is a test source, whose method names describe
 * behaviour.
 */
@BugPattern(
    summary = "Name the method in camel case, starting in lower case and without underscores or dollar signs",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessMethodNameIsCamelCase extends BugChecker implements MethodTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Pattern UNCONVENTIONAL = Pattern.compile("^[A-Z].*|^[^_].*[_$].*");

    @Override
    public Description matchMethod(MethodTree method, VisitorState state) {
        Symbol.MethodSymbol symbol = ASTHelpers.getSymbol(method);
        boolean ordinary = symbol.getKind() == ElementKind.METHOD && symbol.owner.getKind().isClass();
        boolean candidate = ordinary && UNCONVENTIONAL.matcher(symbol.getSimpleName()).matches();
        boolean original = ASTHelpers.findSuperMethods(symbol, state.getTypes()).isEmpty();
        String path = state.getPath().getCompilationUnit().getSourceFile().toUri().getPath();
        return candidate && original && !path.contains("/src/test/") ? this.describeMatch(method)
            : Description.NO_MATCH;
    }
}
