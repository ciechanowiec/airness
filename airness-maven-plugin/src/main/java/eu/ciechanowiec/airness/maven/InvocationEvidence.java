package eu.ciechanowiec.airness.maven;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.experimental.UtilityClass;
import org.eclipse.aether.SessionData;

/**
 * Starts a module's test evidence once per Maven invocation, preserving all of its test phases.
 */
@UtilityClass
final class InvocationEvidence {

    static void prepare(SessionData session, Path build, Path coverage) {
        String module = build.toAbsolutePath().normalize().toString();
        if (OncePerSession.firstRun(session, InvocationEvidence.class, module)) {
            clear(coverage);
            clear(build.resolve("airness/spring-context.evidence"));
        }
    }

    private static void clear(Path evidence) {
        try {
            Files.deleteIfExists(evidence);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not reset test evidence " + evidence, exception);
        }
    }
}
