package eu.ciechanowiec.airness.spring;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/**
 * Fails a Spring test when the application reported an error the test did not declare.
 *
 * <p>Spring Test loads this listener from {@code spring.factories} into every test context, so a project receives
 * the check without declaring anything. The errors recorded while a test method runs, which includes the start of
 * an application that method was the first to need, are judged when it ends against the {@link ExpectedServerError}
 * declarations it carries. The errors recorded outside every test method of a class, such as in its class-level
 * setup, are judged when the class ends, where nothing can declare them.
 *
 * <p>An error is charged to the test that was running when it was recorded, which holds only while one test runs
 * at a time, so a test that starts while another is still running fails rather than borrowing its errors. The
 * listener runs first, which makes it the last to finish a test, so an error another listener's teardown provokes
 * is still charged to the test it tore down.
 */
public final class ServerErrorListener implements TestExecutionListener, Ordered {

    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }

    @Override
    public void beforeTestClass(TestContext testContext) {
        if (!ServerErrorCapture.readable()) {
            throw new IllegalStateException(
                """
                    The application's errors cannot be read, because SLF4J does not log through Logback in this test \
                    JVM. Airness fails a Spring test on an error the application logs and reads those errors from \
                    Logback, which Spring Boot logs through unless a project replaces it. Log through Logback."""
            );
        }
        if (ServerErrorRecord.current().flatMap(ServerErrorRecord::running).isPresent()) {
            throw concurrent(testContext.getTestClass().getName());
        }
        ServerErrorRecord.open();
    }

    @Override
    public void beforeTestMethod(TestContext testContext) {
        ServerErrorRecord errors = recordOf(testContext);
        errors.install();
        if (!errors.begin(testContext.getTestMethod())) {
            throw concurrent(named(testContext.getTestMethod()));
        }
    }

    @Override
    public void afterTestMethod(TestContext testContext) {
        ServerErrorRecord errors = recordOf(testContext);
        try {
            judge(testContext.getTestMethod(), errors.drain());
        } finally {
            errors.end();
        }
    }

    @Override
    public void afterTestClass(TestContext testContext) {
        if (ServerErrorCapture.readable()) {
            ServerErrorRecord.current().ifPresent(errors -> close(errors, testContext.getTestClass()));
        }
    }

    private static ServerErrorRecord recordOf(TestContext testContext) {
        return ServerErrorRecord.current().orElseThrow(
            () -> new IllegalStateException(
                """
                    The record of the errors the application logs was replaced while %s ran. Airness keeps it as the \
                    default handler of uncaught exceptions for as long as a test class runs. Remove the code that sets \
                    that handler while the tests run.""".formatted(testContext.getTestClass().getName())
            )
        );
    }

    private static IllegalStateException concurrent(String test) {
        return new IllegalStateException(
            """
                %s started while another test was still running. Airness charges an error the application logs to the \
                test running when it was logged, which needs the tests of a Spring project to run one at a time, as \
                JUnit runs them by default. Remove the setting that runs them concurrently.""".formatted(test)
        );
    }

    private static void close(ServerErrorRecord errors, Class<?> testClass) {
        try {
            String outside = section(
                """
                    The application reported errors while %s ran outside any of its test methods, where no test can \
                    declare them. Repair what reported each one.""".formatted(testClass.getName()),
                errors.drain(), ServerError::described
            );
            if (!outside.isEmpty()) {
                throw new AssertionError(outside);
            }
        } finally {
            errors.close();
        }
    }

    private static void judge(Method method, Collection<ServerError> errors) {
        Collection<ExpectedServerError> declared = AnnotatedElementUtils.findMergedRepeatableAnnotations(
            method, ExpectedServerError.class
        );
        List<ExpectedServerError> unreasoned = declared.stream()
            .filter(declaration -> declaration.reason().isBlank())
            .toList();
        List<ServerError> undeclared = errors.stream()
            .filter(error -> declared.stream().noneMatch(declaration -> error.loggedBy(declaration.logger())))
            .toList();
        List<ExpectedServerError> unprovoked = declared.stream()
            .filter(declaration -> errors.stream().noneMatch(error -> error.loggedBy(declaration.logger())))
            .toList();
        String name = named(method);
        String failures = Stream.of(
            section(
                "%s declares server errors without a reason. State why the test provokes each one.".formatted(name),
                unreasoned, ServerErrorListener::logger
            ),
            section(
                """
                    The application reported errors that %s did not declare. Repair what reported each one. Where the \
                    test provokes one on purpose, declare it on the test method with \
                    @ExpectedServerError(logger = ..., reason = ...).""".formatted(name),
                undeclared, ServerError::described
            ),
            section(
                """
                    %s declares server errors it never provoked. Remove each declaration, or restore the error it was \
                    written for.""".formatted(name),
                unprovoked, ServerErrorListener::logger
            )
        ).filter(failure -> !failure.isEmpty()).collect(Collectors.joining(System.lineSeparator()));
        if (!failures.isEmpty()) {
            throw new AssertionError(failures);
        }
    }

    // A heading followed by one line per item, or nothing at all when there is no item to report.
    private static <T> String section(String heading, Collection<T> items, Function<T, String> described) {
        return items.isEmpty()
            ? ""
            : items.stream()
                .map(item -> "%n  - %s".formatted(described.apply(item)))
                .collect(Collectors.joining("", heading, ""));
    }

    private static String logger(ExpectedServerError declaration) {
        return declaration.logger().getName();
    }

    private static String named(Method method) {
        return "%s.%s".formatted(method.getDeclaringClass().getName(), method.getName());
    }
}
