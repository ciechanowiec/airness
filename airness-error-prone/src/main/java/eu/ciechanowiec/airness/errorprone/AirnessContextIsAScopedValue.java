package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.ClassTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.MemberReferenceTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.NewClassTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import java.io.Serial;
import java.util.List;

/**
 * Carries the context of a unit of work in a {@link ScopedValue}, a request-scoped bean or a parameter rather
 * than in a {@link ThreadLocal}.
 *
 * <p>Work runs on virtual threads, one per task, so a value held per thread is a value held per task: a
 * cache kept in one is rebuilt on every task, and a context set in one is inherited by nothing the task hands
 * work to. Reported are a new {@link ThreadLocal} of any class, {@link InheritableThreadLocal} and Spring's
 * named forms among them, {@link ThreadLocal#withInitial}, a reference to either, and a class extending
 * {@link ThreadLocal}. A framework keeping its own thread-bound state behind an API, as the security context
 * and the logging context do, is not reported. The check admits no suppression.
 */
@BugPattern(
    summary = "Carry the context of a unit of work in a ScopedValue, a request-scoped bean or a parameter rather"
        + " than a ThreadLocal, which holds one value per virtual thread and so one per task",
    severity = BugPattern.SeverityLevel.ERROR,
    suppressionAnnotations = {}
)
public final class AirnessContextIsAScopedValue extends BugChecker
                                                implements NewClassTreeMatcher, MethodInvocationTreeMatcher,
                                                MemberReferenceTreeMatcher, ClassTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final List<String> THREAD_LOCAL = List.of("java.lang.ThreadLocal");

    @Override
    public Description matchNewClass(NewClassTree creation, VisitorState state) {
        Type created = ASTHelpers.getType(creation.getIdentifier());
        return KnownTypes.isAnyOf(created, THREAD_LOCAL, state) ? this.describeMatch(creation) : Description.NO_MATCH;
    }

    @Override
    public Description matchMethodInvocation(MethodInvocationTree call, VisitorState state) {
        return threadBound(ASTHelpers.getSymbol(call), state) ? this.describeMatch(call) : Description.NO_MATCH;
    }

    @Override
    public Description matchMemberReference(MemberReferenceTree reference, VisitorState state) {
        Symbol.MethodSymbol method = ASTHelpers.getSymbol(reference);
        boolean constructed = method.isConstructor() && KnownTypes.isAnyOf(method.owner.type, THREAD_LOCAL, state);
        return constructed || threadBound(method, state) ? this.describeMatch(reference) : Description.NO_MATCH;
    }

    @Override
    public Description matchClass(ClassTree declaration, VisitorState state) {
        Symbol.ClassSymbol symbol = ASTHelpers.getSymbol(declaration);
        boolean extending = !symbol.isAnonymous() && KnownTypes.isAnyOf(symbol.getSuperclass(), THREAD_LOCAL, state);
        return extending ? this.describeMatch(declaration) : Description.NO_MATCH;
    }

    private static boolean threadBound(Symbol.MethodSymbol method, VisitorState state) {
        return method.isStatic() && "withInitial".contentEquals(method.getSimpleName())
            && KnownTypes.isAnyOf(method.owner.type, THREAD_LOCAL, state);
    }
}
