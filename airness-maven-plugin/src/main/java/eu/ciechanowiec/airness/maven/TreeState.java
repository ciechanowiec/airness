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
 * Stores one tree fingerprint per module for exactly one Maven session.
 */
@UtilityClass
final class TreeState {

    private static final Object KEY = new Object();
    private static final Lock LOCK = new ReentrantLock();

    static void snapshot(SessionData data, Path root, String scope) {
        snapshot(data, scope, TreeFingerprint.from(root));
    }

    static void snapshot(SessionData data, String scope, String fingerprint) {
        store(data, scope, fingerprint);
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
            Stream<Map.Entry<String, String>> replacement = Stream.of(Map.entry(scope, fingerprint));
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
