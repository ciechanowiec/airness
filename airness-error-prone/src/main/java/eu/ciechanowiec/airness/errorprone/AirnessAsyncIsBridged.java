package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MemberReferenceTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.NewClassTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.code.Symbol;
import java.io.Serial;
import java.util.List;
import java.util.Set;

/**
 * Calls the blocking form on the current virtual thread rather than starting asynchronous or reactive work.
 *
 * <p>Reported are {@code CompletableFuture.supplyAsync} and {@code runAsync}, which run the work on the common
 * pool that is sized for the processor, every {@code *Async} method of a {@code CompletionStage}, a new
 * {@code SubmissionPublisher}, and every call, construction and method reference of a Reactor, RxJava, Mutiny
 * or Reactive Streams type. A library that answers with a publisher is received through the bridges that wait
 * for it, {@code block}, {@code blockFirst}, {@code blockLast}, {@code blockOptional}, {@code toIterable},
 * {@code toStream} and {@code toFuture}, which are not reported, and neither is waiting on a future a library
 * returns.
 * The check admits no suppression.
 */
@BugPattern(
    summary = "Call the blocking form on the current virtual thread rather than starting asynchronous or reactive"
        + " work; receive a library's publisher through block, toIterable or toStream",
    severity = BugPattern.SeverityLevel.ERROR,
    suppressionAnnotations = {}
)
public final class AirnessAsyncIsBridged extends BugChecker
                                         implements MethodInvocationTreeMatcher, NewClassTreeMatcher,
                                         MemberReferenceTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final List<String> REACTIVE = List.of(
        "reactor", "io.reactivex", "io.smallrye.mutiny", "org.reactivestreams"
    );

    private static final Set<String> BRIDGES = Set.of(
        "block", "blockFirst", "blockLast", "blockOptional", "toIterable", "toStream", "toFuture"
    );

    private static final Set<String> STARTERS = Set.of("supplyAsync", "runAsync");

    private static final List<String> STAGE = List.of("java.util.concurrent.CompletionStage");

    private static final List<String> PUBLISHER = List.of("java.util.concurrent.SubmissionPublisher");

    @Override
    public Description matchMethodInvocation(MethodInvocationTree call, VisitorState state) {
        return this.judge(call, ASTHelpers.getSymbol(call), state);
    }

    @Override
    public Description matchNewClass(NewClassTree creation, VisitorState state) {
        return this.judge(creation, ASTHelpers.getSymbol(creation), state);
    }

    @Override
    public Description matchMemberReference(MemberReferenceTree reference, VisitorState state) {
        return this.judge(reference, ASTHelpers.getSymbol(reference), state);
    }

    private Description judge(Tree tree, Symbol.MethodSymbol method, VisitorState state) {
        boolean refused = starts(method) || chains(method, state) || publishes(method, state) || reactive(method);
        return refused ? this.describeMatch(tree) : Description.NO_MATCH;
    }

    private static boolean starts(Symbol.MethodSymbol method) {
        return method.isStatic() && STARTERS.contains(method.getSimpleName().toString())
            && "java.util.concurrent.CompletableFuture".equals(method.owner.getQualifiedName().toString());
    }

    private static boolean chains(Symbol.MethodSymbol method, VisitorState state) {
        return !method.isStatic() && method.getSimpleName().toString().endsWith("Async")
            && KnownTypes.isAnyOf(method.owner.type, STAGE, state);
    }

    private static boolean publishes(Symbol.MethodSymbol method, VisitorState state) {
        return method.isConstructor() && KnownTypes.isAnyOf(method.owner.type, PUBLISHER, state);
    }

    private static boolean reactive(Symbol.MethodSymbol method) {
        return KnownTypes.declaredIn(method.owner, REACTIVE) && !BRIDGES.contains(method.getSimpleName().toString());
    }
}
