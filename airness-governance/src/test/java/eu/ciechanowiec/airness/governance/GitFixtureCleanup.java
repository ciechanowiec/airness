package eu.ciechanowiec.airness.governance;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;

/**
 * Ends the {@link GitFixture} repositories of a test JVM once each launcher execution is over.
 *
 * <p>JUnit registers this extension for every test class through its service file, which
 * {@code junit-platform.properties} lets it detect, so no test class has to name it. The conclusion is
 * held in the store of the root context, which JUnit closes when the launcher execution ends: once for
 * the whole run under a single Surefire execution, and once per test class when Surefire forks per class
 * or reruns failing tests. A failed or aborted test or test class is recorded beside the fixtures rather
 * than in this execution, so it holds for every later execution of the JVM, and each closing deletes the
 * fixtures only while no failure has been recorded.
 */
public final class GitFixtureCleanup implements BeforeAllCallback, AfterAllCallback, TestWatcher {

    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace.create(
        GitFixtureCleanup.class
    );

    @Override
    public void beforeAll(ExtensionContext context) {
        context.getRoot().getStore(NAMESPACE).computeIfAbsent(GitFixtureCleanup.class, _ -> conclusion());
    }

    @Override
    public void afterAll(ExtensionContext context) {
        context.getExecutionException().ifPresent(_ -> GitFixture.recordFailure());
    }

    @Override
    public void testFailed(ExtensionContext context, @Nullable Throwable cause) {
        GitFixture.recordFailure();
    }

    @Override
    public void testAborted(ExtensionContext context, @Nullable Throwable cause) {
        GitFixture.recordFailure();
    }

    private static AutoCloseable conclusion() {
        return GitFixture::conclude;
    }
}
