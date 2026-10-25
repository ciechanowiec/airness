package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.ReportSource;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.experimental.UtilityClass;
import org.eclipse.aether.SessionData;

/**
 * The source identity a compliance report describes, captured once at the start of one Maven invocation.
 */
@UtilityClass
final class ReportSession {

    private static final String SOURCE = ReportSession.class.getName() + ".source";
    private static final Lock LOCK = new ReentrantLock();

    static void begin(SessionData data, Path root) {
        LOCK.lock();
        try {
            if (!(data.get(SOURCE) instanceof ReportSource)) {
                data.set(SOURCE, ReportSource.read(root, Instant.now().toString()));
            }
        } finally {
            LOCK.unlock();
        }
    }

    static ReportSource source(SessionData data) {
        if (data.get(SOURCE) instanceof ReportSource source) {
            return source;
        }
        throw new IllegalStateException("Compliance input preparation did not run in this Maven invocation");
    }
}
