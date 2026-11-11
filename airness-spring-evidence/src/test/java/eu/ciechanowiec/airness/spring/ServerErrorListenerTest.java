package eu.ciechanowiec.airness.spring;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import lombok.SneakyThrows;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestContextManager;

/**
 * When a Spring test fails on an error the application reported, and when it does not.
 */
class ServerErrorListenerTest {

    private static final Logger LOG = LoggerFactory.getLogger(ServerErrorListenerTest.class);
    private static final String SOURCE = ServerErrorListenerTest.class.getName();
    private static final String UNDECLARED = "undeclared";
    private static final String DECLARED = "declared";
    private final ServerErrorListener listener;

    ServerErrorListenerTest() {
        this.listener = new ServerErrorListener();
    }

    @BeforeEach
    void startTheClass() {
        this.listener.beforeTestClass(context(UNDECLARED));
        drain();
    }

    @AfterEach
    void endTheClass() {
        drain();
        this.listener.afterTestClass(context(UNDECLARED));
    }

    // Every error this test logs is logged here, so what tells one apart is the text it is handed.
    private static void report(String message) {
        LOG.error("{}", message);
    }

    private static void drain() {
        ServerErrorRecord.current().orElseThrow().drain();
    }

    @SneakyThrows
    private static TestContext context(String method) {
        TestContext context = new TestContextManager(Provocations.class).getTestContext();
        context.updateState(null, Provocations.class.getDeclaredMethod(method), null);
        return context;
    }

    // Runs one test method around what it reports, and answers why the test failed, or nothing.
    private String run(String method, Runnable reporting) {
        TestContext context = context(method);
        this.listener.beforeTestMethod(context);
        reporting.run();
        return String.valueOf(
            assertThrows(AssertionError.class, () -> this.listener.afterTestMethod(context)).getMessage()
        );
    }

    private void assertPasses(String method, Runnable reporting) {
        TestContext context = context(method);
        this.listener.beforeTestMethod(context);
        reporting.run();
        assertDoesNotThrow(() -> this.listener.afterTestMethod(context));
    }

    @Test
    void runsFirstSoThatItFinishesEachTestLast() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE, this.listener.getOrder());
    }

    @Test
    void failsATestOnAnErrorItDidNotDeclare() {
        String failure = this.run(UNDECLARED, () -> report("refused order"));
        assertAll(
            () -> assertTrue(failure.contains(Provocations.class.getName() + ".undeclared did not declare"), failure),
            () -> assertTrue(failure.contains(SOURCE + ": refused order"), failure),
            () -> assertTrue(failure.contains("@ExpectedServerError(logger = ..., reason = ...)"), failure)
        );
    }

    @Test
    void passesATestWithoutErrors() {
        this.assertPasses(UNDECLARED, () -> LOG.warn("nothing failed"));
    }

    @Test
    void passesATestOnTheErrorsItDeclares() {
        this.assertPasses(
            DECLARED, () -> {
                report("first");
                report("second");
            }
        );
    }

    @Test
    void passesATestOnErrorsLoggedBeneathTheDeclaredClass() {
        this.assertPasses(DECLARED, Nested::report);
    }

    @Test
    void failsATestOnAnErrorFromALoggerItDidNotDeclare() {
        String failure = this.run(
            DECLARED, () -> {
                report("provoked");
                LoggerFactory.getLogger(Provocations.class).error("elsewhere");
            }
        );
        assertTrue(failure.contains(Provocations.class.getName() + ": elsewhere"), failure);
    }

    @Test
    void failsATestThatDeclaresAnErrorItNeverProvoked() {
        String failure = this.run(DECLARED, () -> LOG.warn("nothing was provoked"));
        assertAll(
            () -> assertTrue(failure.contains("declares server errors it never provoked"), failure),
            () -> assertTrue(failure.contains("  - " + SOURCE), failure)
        );
    }

    @Test
    void failsATestOnTheOneOfTwoDeclarationsItNeverProvoked() {
        String failure = this.run("declaredTwice", () -> report("provoked"));
        assertAll(
            () -> assertTrue(failure.contains("  - " + Provocations.class.getName()), failure),
            () -> assertFalse(failure.contains("  - " + SOURCE), failure)
        );
    }

    @Test
    void failsATestThatDeclaresAnErrorWithoutAReason() {
        String failure = this.run("unreasoned", () -> report("provoked"));
        assertTrue(failure.contains("declares server errors without a reason"), failure);
    }

    @Test
    void failsTheClassOnAnErrorLoggedOutsideItsTestMethods() {
        report("set up");
        AssertionError failure = assertThrows(
            AssertionError.class, () -> this.listener.afterTestClass(context(UNDECLARED))
        );
        this.listener.beforeTestClass(context(UNDECLARED));
        String message = String.valueOf(failure.getMessage());
        assertAll(
            () -> assertTrue(message.contains("outside any of its test methods"), message),
            () -> assertTrue(message.contains("  - %s: set up".formatted(SOURCE)), message)
        );
    }

    @Test
    void refusesATestThatStartsWhileAnotherRuns() {
        TestContext running = context(UNDECLARED);
        this.listener.beforeTestMethod(running);
        IllegalStateException refused = assertThrows(
            IllegalStateException.class, () -> this.listener.beforeTestMethod(running)
        );
        this.listener.afterTestMethod(running);
        this.listener.afterTestMethod(running);
        assertTrue(String.valueOf(refused.getMessage()).contains("started while another test was still running"));
    }

    @Test
    void refusesAClassThatStartsWhileATestRuns() {
        TestContext running = context(UNDECLARED);
        this.listener.beforeTestMethod(running);
        IllegalStateException refused = assertThrows(
            IllegalStateException.class, () -> this.listener.beforeTestClass(running)
        );
        this.listener.afterTestMethod(running);
        assertTrue(String.valueOf(refused.getMessage()).contains("started while another test was still running"));
    }

    @Test
    void failsATestWhoseRecordWasReplaced() {
        ServerErrorRecord errors = ServerErrorRecord.current().orElseThrow();
        Thread.UncaughtExceptionHandler other = (_, _) -> {
        };
        Thread.setDefaultUncaughtExceptionHandler(other);
        IllegalStateException failure = assertThrows(
            IllegalStateException.class, () -> this.listener.beforeTestMethod(context(UNDECLARED))
        );
        Thread.setDefaultUncaughtExceptionHandler(errors);
        assertTrue(String.valueOf(failure.getMessage()).contains("was replaced while"));
    }

    /**
     * A class whose logger is named beneath the listener test's.
     */
    private static final class Nested {

        private static final Logger LOG = LoggerFactory.getLogger(Nested.class);

        private static void report() {
            LOG.error("nested");
        }
    }
}
