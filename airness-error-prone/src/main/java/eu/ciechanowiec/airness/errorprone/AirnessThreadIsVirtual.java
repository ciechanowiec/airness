package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MemberReferenceTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.MethodInvocationTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.NewClassTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.MemberReferenceTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.TypeTag;
import java.io.Serial;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Runs work on virtual threads rather than on platform threads and the pools that hold them.
 *
 * <p>On the Java this harness requires, a virtual thread blocks without holding an operating-system thread,
 * so a pool sized to ration platform threads rations nothing worth rationing, and a pool of virtual threads
 * is wrong in itself. Reported are the {@code Executors} factories that build a platform pool or a platform
 * thread factory, a new {@link Thread} of any class, {@link Thread#ofPlatform()}, and a new
 * {@code ThreadPoolExecutor}, {@code ScheduledThreadPoolExecutor}, {@code ForkJoinPool}, {@code Timer},
 * Spring {@code ThreadPoolTaskExecutor} or {@code ThreadPoolTaskScheduler}, whether written as a call, a
 * construction or a method reference.
 *
 * <p>One construction is not reported: {@link Thread#Thread(ThreadGroup, Runnable, String, long)} with a positive
 * constant stack size, because a stack of a chosen size is the one thing a virtual thread cannot have.
 * Parallel streams and the common {@code ForkJoinPool} remain the place for work that is bound by the
 * processor. The check admits no suppression.
 */
@BugPattern(
    summary = "Run the work on a virtual thread: Executors.newVirtualThreadPerTaskExecutor() or Thread.ofVirtual(),"
        + " in Spring the AsyncTaskExecutor or TaskScheduler Spring Boot configures, and work bound by the"
        + " processor on a parallel stream",
    severity = BugPattern.SeverityLevel.ERROR,
    suppressionAnnotations = {}
)
public final class AirnessThreadIsVirtual extends BugChecker
                                          implements MethodInvocationTreeMatcher, NewClassTreeMatcher,
                                          MemberReferenceTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final String THREAD = "java.lang.Thread";

    private static final String EXECUTORS = "java.util.concurrent.Executors";

    private static final Set<String> PLATFORM_FACTORIES = Set.of(
        "newFixedThreadPool", "newCachedThreadPool", "newSingleThreadExecutor", "newScheduledThreadPool",
        "newSingleThreadScheduledExecutor", "newWorkStealingPool", "defaultThreadFactory"
    );

    private static final List<String> PLATFORM_TYPES = List.of(
        THREAD, "java.util.concurrent.ThreadPoolExecutor", "java.util.concurrent.ForkJoinPool", "java.util.Timer",
        "org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor",
        "org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler"
    );

    private static final int STACK_SIZE = 3;

    @Override
    public Description matchMethodInvocation(MethodInvocationTree call, VisitorState state) {
        return factory(ASTHelpers.getSymbol(call)) ? this.describeMatch(call) : Description.NO_MATCH;
    }

    @Override
    public Description matchMemberReference(MemberReferenceTree reference, VisitorState state) {
        Symbol.MethodSymbol method = ASTHelpers.getSymbol(reference);
        boolean constructed = method.isConstructor() && KnownTypes.isAnyOf(method.owner.type, PLATFORM_TYPES, state);
        return constructed || factory(method) ? this.describeMatch(reference) : Description.NO_MATCH;
    }

    @Override
    public Description matchNewClass(NewClassTree creation, VisitorState state) {
        Symbol.MethodSymbol constructor = ASTHelpers.getSymbol(creation);
        boolean platform = KnownTypes.isAnyOf(ASTHelpers.getType(creation.getIdentifier()), PLATFORM_TYPES, state);
        return platform && !sized(creation, constructor) ? this.describeMatch(creation) : Description.NO_MATCH;
    }

    private static boolean factory(Symbol.MethodSymbol method) {
        String owner = method.owner.getQualifiedName().toString();
        String name = method.getSimpleName().toString();
        boolean pool = EXECUTORS.equals(owner) && PLATFORM_FACTORIES.contains(name);
        boolean builder = THREAD.equals(owner) && "ofPlatform".equals(name);
        return method.isStatic() && (pool || builder);
    }

    private static boolean sized(NewClassTree creation, Symbol.MethodSymbol constructor) {
        boolean takesStack = THREAD.equals(constructor.owner.getQualifiedName().toString())
            && constructor.getParameters().size() > STACK_SIZE
            && constructor.getParameters().get(STACK_SIZE).type.hasTag(TypeTag.LONG);
        return takesStack && positive(creation.getArguments().get(STACK_SIZE));
    }

    private static boolean positive(ExpressionTree size) {
        return Optional.ofNullable(ASTHelpers.constValue(size, Number.class))
            .filter(value -> value.longValue() > 0)
            .isPresent();
    }
}
