package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.dataflow.nullnesspropagation.Nullness;
import com.google.errorprone.dataflow.nullnesspropagation.NullnessAnalysis;
import com.google.errorprone.dataflow.nullnesspropagation.NullnessAnnotations;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.matchers.Matcher;
import com.google.errorprone.matchers.method.MethodMatchers;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import java.io.Serial;
import java.util.Optional;
import javax.lang.model.element.ElementKind;
import javax.tools.JavaFileObject;

/**
 * Uses a string directly rather than passing it through {@link String#valueOf(Object)}.
 *
 * <p>The conversion is redundant only for a string that cannot be null, since it otherwise answers a
 * null with the text {@code "null"}. A string counts as non-null when the flow analysis proves it, or
 * when it is a parameter, a field or the result of a method compiled with the code under check and
 * none of them is declared nullable, which is the contract NullAway enforces there. A local variable
 * without a proven value and the result of a library method are left alone, since a library may
 * answer null where its declaration does not say so. A call used as the receiver of another call is
 * left alone too, since there the conversion also states how a null receiver is answered.
 */
@BugPattern(
    summary = "The value is already a non-null String; use it rather than String.valueOf(value)",
    severity = BugPattern.SeverityLevel.ERROR
)
public final class AirnessValueOfStringIsTheString extends BugChecker implements MethodInvocationTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Matcher<ExpressionTree> VALUE_OF = MethodMatchers.staticMethod()
        .onClass("java.lang.String")
        .named("valueOf");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree invocation, VisitorState state) {
        boolean ofString = invocation.getArguments().size() == 1 && ASTHelpers.isSameType(
            ASTHelpers.getType(invocation.getArguments().getFirst()), state.getSymtab().stringType, state
        );
        boolean receiver = state.getPath().getParentPath().getLeaf() instanceof MemberSelectTree;
        boolean applicable = ofString && !receiver && VALUE_OF.matches(invocation, state);
        return applicable && nonNull(invocation.getArguments().getFirst(), state)
            ? this.describeMatch(invocation) : Description.NO_MATCH;
    }

    private static boolean nonNull(ExpressionTree argument, VisitorState state) {
        Nullness inferred = NullnessAnalysis.instance(state.context)
            .getNullness(new TreePath(state.getPath(), argument), state.context);
        return inferred == Nullness.NONNULL || Optional.ofNullable(ASTHelpers.getSymbol(argument))
            .filter(AirnessValueOfStringIsTheString::declaredNonNull)
            .isPresent();
    }

    private static boolean declaredNonNull(Symbol symbol) {
        boolean member = symbol instanceof VarSymbol variable && variable.getKind() != ElementKind.LOCAL_VARIABLE;
        boolean compiledResult = symbol instanceof MethodSymbol method && compiled(method.enclClass());
        boolean declared = member || compiledResult;
        return declared && NullnessAnnotations.fromAnnotationsOn(symbol).orElse(Nullness.NONNULL) == Nullness.NONNULL;
    }

    private static boolean compiled(ClassSymbol owner) {
        return Optional.ofNullable(owner.classfile)
            .filter(file -> file.getKind() == JavaFileObject.Kind.SOURCE)
            .isPresent();
    }
}
