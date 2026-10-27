package eu.ciechanowiec.airness.maven;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import lombok.experimental.UtilityClass;
import org.eclipse.aether.SessionData;

/**
 * One lock shared by every Airness plugin realm in a Maven invocation.
 *
 * <p>A stack layer adds plugin dependencies and therefore creates another classloader. Static fields and
 * plugin-owned record types cannot carry state between those realms. The session owns this JDK lock under
 * a stable string key, and its atomic initialization makes every realm use the same concurrency boundary.
 */
@UtilityClass
final class SessionLock {

    static Lock of(SessionData data) {
        return (Lock) data.computeIfAbsent(SessionLock.class.getName(), ReentrantLock::new);
    }
}
