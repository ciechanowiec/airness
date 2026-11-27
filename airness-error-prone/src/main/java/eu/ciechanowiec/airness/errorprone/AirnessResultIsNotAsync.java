package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.MethodTreeMatcher;
import com.google.errorprone.bugpatterns.BugChecker.VariableTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.Symbol;
import java.io.Serial;
import java.util.List;
import javax.lang.model.element.ElementKind;

/**
 * Returns and holds values rather than the promise of one.
 *
 * <p>A virtual thread that waits costs nothing worth saving, so a method answers with its result and a caller
 * that needs it waits for it. A future or a publisher in a signature the project declares hands every caller
 * a second way to fail and a second place for an exception to be lost. Reported are a method return type and
 * a field, a record component among them, whose type is a {@code Future}, a {@code CompletionStage}, a
 * {@code Flow.Publisher}, a Reactive Streams {@code Publisher} such as Reactor's {@code Mono} and {@code Flux},
 * or Spring's {@code DeferredResult} or {@code WebAsyncTask}, and a {@code Callable} returned by a Spring
 * request handler, which Spring runs asynchronously.
 *
 * <p>A method that overrides another is not reported, because the type it overrides decides its signature. The
 * declaration of the project's own supertype is reported instead. A future a library returns may be held in a
 * local variable or passed to a parameter and waited on, which is how a blocking caller receives it. The check
 * admits no suppression.
 */
@BugPattern(
    summary = "Return the value itself and hold no future or publisher; wait on a future a library returns where"
        + " it is received",
    severity = BugPattern.SeverityLevel.ERROR,
    suppressionAnnotations = {}
)
public final class AirnessResultIsNotAsync extends BugChecker implements MethodTreeMatcher, VariableTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final List<String> PROMISES = List.of(
        "java.util.concurrent.Future", "java.util.concurrent.CompletionStage", "java.util.concurrent.Flow$Publisher",
        "org.reactivestreams.Publisher", "org.springframework.web.context.request.async.DeferredResult",
        "org.springframework.web.context.request.async.WebAsyncTask"
    );

    private static final List<String> CALLABLE = List.of("java.util.concurrent.Callable");

    private static final List<String> HANDLERS = List.of(
        "RequestMapping", "GetMapping", "PostMapping", "PutMapping", "DeleteMapping", "PatchMapping"
    );

    @Override
    public Description matchMethod(MethodTree method, VisitorState state) {
        Symbol.MethodSymbol symbol = ASTHelpers.getSymbol(method);
        return declared(symbol, state) && promised(symbol, state) ? this.describeMatch(method) : Description.NO_MATCH;
    }

    @Override
    public Description matchVariable(VariableTree variable, VisitorState state) {
        Symbol.VarSymbol symbol = ASTHelpers.getSymbol(variable);
        boolean field = symbol.getKind() == ElementKind.FIELD;
        return field && KnownTypes.isAnyOf(symbol.type, PROMISES, state)
            ? this.describeMatch(variable)
            : Description.NO_MATCH;
    }

    private static boolean declared(Symbol.MethodSymbol symbol, VisitorState state) {
        return !symbol.isConstructor() && (symbol.flags() & Flags.GENERATED_MEMBER) == 0
            && ASTHelpers.findSuperMethods(symbol, state.getTypes()).isEmpty();
    }

    private static boolean promised(Symbol.MethodSymbol symbol, VisitorState state) {
        boolean promise = KnownTypes.isAnyOf(symbol.getReturnType(), PROMISES, state);
        boolean handed = handler(symbol, state) && KnownTypes.isAnyOf(symbol.getReturnType(), CALLABLE, state);
        return promise || handed;
    }

    private static boolean handler(Symbol.MethodSymbol symbol, VisitorState state) {
        return HANDLERS.stream().anyMatch(
            name -> ASTHelpers.hasAnnotation(symbol, "org.springframework.web.bind.annotation." + name, state)
        );
    }
}
