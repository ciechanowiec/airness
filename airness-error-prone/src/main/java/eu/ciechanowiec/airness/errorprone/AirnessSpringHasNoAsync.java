package eu.ciechanowiec.airness.errorprone;

import com.google.errorprone.BugPattern;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker;
import com.google.errorprone.bugpatterns.BugChecker.AnnotationTreeMatcher;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.AnnotationTree;
import java.io.Serial;
import java.util.Optional;
import java.util.Set;

/**
 * Calls a Spring bean's method directly rather than through {@code @Async}.
 *
 * <p>A request already runs on a virtual thread, so a method that blocks costs its caller nothing worth moving
 * elsewhere, and work that should run beside it is submitted to the {@code AsyncTaskExecutor} Spring Boot
 * configures, where the caller holds what it submitted. {@code @Async} adds a proxy that a call from the same
 * bean bypasses, a result that has to be a future and an exception that a void method gives nowhere to arrive.
 * Reported are {@code @Async} and {@code @EnableAsync}. The check admits no suppression.
 */
@BugPattern(
    summary = "Call the method directly, and submit work that runs beside the caller to the AsyncTaskExecutor"
        + " Spring Boot configures, rather than declaring @Async",
    severity = BugPattern.SeverityLevel.ERROR,
    suppressionAnnotations = {}
)
public final class AirnessSpringHasNoAsync extends BugChecker implements AnnotationTreeMatcher {

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Set<String> ASYNC = Set.of(
        "org.springframework.scheduling.annotation.Async", "org.springframework.scheduling.annotation.EnableAsync"
    );

    @Override
    public Description matchAnnotation(AnnotationTree annotation, VisitorState state) {
        boolean async = Optional.ofNullable(ASTHelpers.getType(annotation.getAnnotationType()))
            .map(type -> type.tsym.getQualifiedName().toString())
            .filter(ASYNC::contains)
            .isPresent();
        return async ? this.describeMatch(annotation) : Description.NO_MATCH;
    }
}
