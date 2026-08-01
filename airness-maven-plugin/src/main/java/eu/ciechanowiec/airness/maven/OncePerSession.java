package eu.ciechanowiec.airness.maven;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.maven.execution.MavenSession;
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
final class OncePerSession {

    private static final Object KEY = new Object();

    private OncePerSession() {
        throw new UnsupportedOperationException("This class is not meant to be instantiated");
    }

    /**
     * Whether this is the first time the given goal has been reached in this session.
     *
     * @param session the session the goal runs in
     * @param goal    the goal class, which is what one run is counted per
     * @return whether the caller is the first, and so the one that should do the work
     */
    @SuppressWarnings("unchecked")
    static boolean firstRun(MavenSession session, Class<?> goal) {
        SessionData data = session.getRepositorySession().getData();
        Set<String> reached = (Set<String>) data.computeIfAbsent(KEY, ConcurrentHashMap::newKeySet);
        return reached.add(goal.getName());
    }
}
