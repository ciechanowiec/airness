package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.Justification;
import eu.ciechanowiec.airness.governance.TreeFingerprint;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.experimental.UtilityClass;
import org.eclipse.aether.SessionData;

/**
 * Stores one tree fingerprint per module for exactly one Maven session, and the first one taken in it.
 *
 * <p>Each module snapshots the tree at its own {@code validate}, which in a reactor comes after the modules
 * before it have built. The first snapshot is the tree the invocation started from, and it is the one the
 * closing comparison holds the finished build to.
 */
@UtilityClass
final class TreeState {

    private static final Object KEY = new Object();
    // No module file is named this, so it cannot collide with a module's scope.
    private static final String FIRST = "<first snapshot of the session>";
    private static final Lock LOCK = new ReentrantLock();

    static void snapshot(SessionData data, Path root, String scope) {
        snapshot(data, scope, TreeFingerprint.from(root));
    }

    static void snapshot(SessionData data, String scope, String fingerprint) {
        store(data, scope, fingerprint);
    }

    /**
     * Whether the tree still matches the first snapshot this session took.
     *
     * @param data session-scoped resolver data
     * @param root the repository root
     * @return whether nothing committable changed since the invocation started
     */
    static boolean unchangedSinceStart(SessionData data, Path root) {
        return unchanged(data, FIRST, TreeFingerprint.from(root));
    }

    static boolean unchanged(SessionData data, Path root, String scope) {
        return unchanged(data, scope, TreeFingerprint.from(root));
    }

    static boolean unchanged(SessionData data, String scope, String fingerprint) {
        String initial = Optional.ofNullable(fingerprints(data).get(scope)).orElseThrow(
            () -> new IllegalStateException("The Airness tree snapshot did not run in this Maven session")
        );
        return initial.equals(fingerprint);
    }

    static String scope(Path projectFile) {
        return projectFile.toAbsolutePath().normalize().toString();
    }

    @Justification("Maven session data is untyped, and this private key stores only this immutable map type")
    @SuppressWarnings("unchecked")
    private static Map<String, String> fingerprints(SessionData data) {
        Object stored = data.get(KEY);
        return Optional.ofNullable(stored)
            .map(value -> (Map<String, String>) value)
            .orElseGet(Map::of);
    }

    @Justification("Maven session data is untyped, and this private key stores only this immutable map type")
    @SuppressWarnings("unchecked")
    private static void store(SessionData data, String scope, String fingerprint) {
        LOCK.lock();
        try {
            Object current = data.get(KEY);
            Map<String, String> held = Optional.ofNullable(current)
                .map(value -> (Map<String, String>) value)
                .orElseGet(Map::of);
            Stream<Map.Entry<String, String>> replacement = Stream.concat(
                Stream.of(Map.entry(scope, fingerprint)),
                held.containsKey(FIRST) ? Stream.empty() : Stream.of(Map.entry(FIRST, fingerprint))
            );
            Map<String, String> update = Stream.concat(held.entrySet().stream(), replacement).collect(
                Collectors.toUnmodifiableMap(
                    Map.Entry::getKey, Map.Entry::getValue, (_, second) -> second
                )
            );
            data.set(KEY, update);
        } finally {
            LOCK.unlock();
        }
    }
}
