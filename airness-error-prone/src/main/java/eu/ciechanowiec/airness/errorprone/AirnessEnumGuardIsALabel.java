package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.CaseTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BindingPatternTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.PatternCaseLabelTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import java.io.Serial;
import java.util.List;
import java.util.Optional;
import javax.lang.model.element.ElementKind;
import org.jspecify.annotations.Nullable;

/**
 * Writes a case that binds an enum only to compare it with one constant as a label naming that constant.
 */
@BugPattern(
    summary = "Label the case with the enum constant rather than binding the enum and guarding on it",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessEnumGuardIsALabel extends BugChecker implements CaseTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    public Description matchCase(CaseTree label, VisitorState state) {
        return binding(label)
            .filter(bound -> guardComparesWithConstant(label, bound))
            .map(_ -> this.describeMatch(label))
            .orElse(Description.NO_MATCH);
    }

    private static Optional<Symbol> binding(CaseTree label) {
        return Optional.of(label.getLabels())
            .filter(labels -> labels.size() == 1)
            .map(List::getFirst)
            .filter(PatternCaseLabelTree.class::isInstance)
            .map(pattern -> ((PatternCaseLabelTree) pattern).getPattern())
            .filter(BindingPatternTree.class::isInstance)
            .map(pattern -> ASTHelpers.getSymbol(((BindingPatternTree) pattern).getVariable()));
    }

    private static boolean guardComparesWithConstant(CaseTree label, Symbol bound) {
        return Optional.ofNullable(label.getGuard())
            .filter(BinaryTree.class::isInstance)
            .map(BinaryTree.class::cast)
            .filter(guard -> guard.getKind() == Tree.Kind.EQUAL_TO)
            .filter(guard -> comparesBound(guard, bound))
            .isPresent();
    }

    private static boolean comparesBound(BinaryTree guard, Symbol bound) {
        return pairs(
            bound, ASTHelpers.getSymbol(guard.getLeftOperand()), ASTHelpers.getSymbol(guard.getRightOperand())
        );
    }

    private static boolean pairs(Symbol bound, @Nullable Symbol left, @Nullable Symbol right) {
        return bound.equals(left) ? isEnumConstant(right) : bound.equals(right) && isEnumConstant(left);
    }

    private static boolean isEnumConstant(@Nullable Symbol symbol) {
        return Optional.ofNullable(symbol).filter(constant -> constant.getKind() == ElementKind.ENUM_CONSTANT)
            .isPresent();
    }
}
