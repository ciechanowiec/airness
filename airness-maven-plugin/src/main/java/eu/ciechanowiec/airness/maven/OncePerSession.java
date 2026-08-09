package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.Justification;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.eclipse.aether.SessionData;

/**
 * Lets a goal that reads the repository run once for the whole session, however many times the
 * lifecycle reaches it.
 *
 * <p>Running once per project is not enough, which is easy to miss. A plugin may fork a lifecycle of
 * its own, and OpenRewrite does exactly that to parse the sources, so every phase up to
 * {@code process-test-classes} is executed twice in one command. Guarding on the top-level project
 * alone leaves a repository-wide check reporting each of its findings twice, and a reader who has seen
 * a finding once stops reading the repeat, which is where a second finding hides.
 *
 * <p>The marker lives in the resolver's session data rather than in a static field of its own, so it
 * lasts exactly as long as the session does. A static set would leak across sessions in an embedded
 * Maven and would then suppress the second build's checks entirely.
 */
@UtilityClass
final class OncePerSession {

    private static final Object KEY = new Object();
    private static final Lock LOCK = new ReentrantLock();

    /**
     * Whether this is the first time the given goal has been reached in this session.
     *
     * @param data session-scoped resolver data
     * @param goal the goal class, which is what one run is counted per
     * @return whether the caller is the first, and so the one that should do the work
     */
    static boolean firstRun(SessionData data, Class<?> goal) {
        return firstRun(data, goal, "session");
    }

    /**
     * Whether this is the first time the goal has reached one named scope in this session.
     *
     * @param data  session-scoped resolver data
     * @param goal  the goal class
     * @param scope the stable scope the goal reads
     * @return whether the caller is the first for that goal and scope
     */
    static boolean firstRun(SessionData data, Class<?> goal, String scope) {
        return claim(data, goal.getName() + ':' + scope);
    }

    @Justification("Maven session data is untyped, and this private key stores only this immutable set type")
    @SuppressWarnings("unchecked")
    private static boolean claim(SessionData data, String marker) {
        LOCK.lock();
        try {
            Object current = data.get(KEY);
            Set<String> reached = Optional.ofNullable(current)
                .map(value -> (Set<String>) value)
                .orElseGet(Set::of);
            if (reached.contains(marker)) {
                return false;
            }
            Set<String> update = Stream.concat(reached.stream(), Stream.of(marker))
                .collect(Collectors.toUnmodifiableSet());
            data.set(KEY, update);
            return true;
        } finally {
            LOCK.unlock();
        }
    }
}
