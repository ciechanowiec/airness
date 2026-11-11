package eu.ciechanowiec.airness.spring;

/**
 * Test methods as a Spring test declares them, which the listener reads but nothing runs.
 */
final class Provocations {

    private static final String REASON = "the test drives the application into the failure on purpose";

    void undeclared() {
        // Declares nothing, so every error it reports is unexpected.
    }

    @ExpectedServerError(logger = ServerErrorListenerTest.class, reason = REASON)
    void declared() {
        // Declares the errors the listener test logs.
    }

    @ExpectedServerError(logger = ServerErrorListenerTest.class, reason = " ")
    void unreasoned() {
        // Declares the errors the listener test logs and says nothing about why.
    }

    @ExpectedServerError(logger = ServerErrorListenerTest.class, reason = REASON)
    @ExpectedServerError(logger = Provocations.class, reason = REASON)
    void declaredTwice() {
        // Declares the listener test's errors and its own.
    }
}
