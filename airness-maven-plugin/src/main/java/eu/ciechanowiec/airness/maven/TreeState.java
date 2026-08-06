package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.Justification;
import eu.ciechanowiec.airness.governance.TreeFingerprint;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.experimental.UtilityClass;
import org.apache.maven.execution.MavenSession;
import org.eclipse.aether.SessionData;

/**
 * Stores one tree fingerprint per module for exactly one Maven session.
 */
@UtilityClass
final class TreeState {

    private static final Object KEY = new Object();

    static void snapshot(MavenSession session, Path root, String scope) {
        fingerprints(session).put(scope, TreeFingerprint.from(root));
    }

    static boolean unchanged(MavenSession session, Path root, String scope) {
        String initial = Optional.ofNullable(fingerprints(session).get(scope)).orElseThrow(
            () -> new IllegalStateException("The Airness tree snapshot did not run in this Maven session")
        );
        return initial.equals(TreeFingerprint.from(root));
    }

    static String scope(Path projectFile) {
        return projectFile.toAbsolutePath().normalize().toString();
    }

    @Justification("Maven session data is untyped, and this private key is written only with this map type")
    @SuppressWarnings("unchecked")
    private static Map<String, String> fingerprints(MavenSession session) {
        SessionData data = session.getRepositorySession().getData();
        return (Map<String, String>) data.computeIfAbsent(KEY, ConcurrentHashMap::new);
    }
}
