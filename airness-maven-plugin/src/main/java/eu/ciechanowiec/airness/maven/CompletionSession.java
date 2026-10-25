package eu.ciechanowiec.airness.maven;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.eclipse.aether.SessionData;

/**
 * The modules that have completed in one Maven invocation, and the one claim on closing.
 *
 * <p>Each module records itself as its last execution. Whichever module completes the set, sequentially or
 * as the last of a parallel build, is told so exactly once, and closing runs there. A module that fails never
 * records itself, so a failed build never closes.
 */
@UtilityClass
final class CompletionSession {

    private static final String KEY = CompletionSession.class.getName();
    private static final Lock LOCK = new ReentrantLock();

    /**
     * Records one module as complete.
     *
     * @param data    session-scoped resolver data
     * @param module  the completed module's id
     * @param awaited the ids of every module whose completion closing waits for
     * @return whether this record completed the set, which happens once per session
     */
    static boolean complete(SessionData data, String module, Collection<String> awaited) {
        LOCK.lock();
        try {
            Completion held = data.get(KEY) instanceof Completion stored ? stored : new Completion(Set.of(), false);
            Set<String> completed = Stream.concat(held.completed().stream(), Stream.of(module))
                .collect(Collectors.toUnmodifiableSet());
            boolean closes = !held.closed() && completed.containsAll(awaited);
            data.set(KEY, new Completion(completed, held.closed() || closes));
            return closes;
        } finally {
            LOCK.unlock();
        }
    }

    /**
     * One immutable session state.
     *
     * @param completed the modules that recorded completion
     * @param closed    whether closing has been claimed
     */
    private record Completion(Set<String> completed, boolean closed) {
    }
}
