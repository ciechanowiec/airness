package eu.ciechanowiec.airness.maven;

import eu.ciechanowiec.airness.governance.TreeFingerprint;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.maven.execution.MavenSession;
import org.eclipse.aether.SessionData;

/** Stores a tree fingerprint for exactly one Maven session. */
final class TreeState {

    private static final Object KEY = new Object();

    private TreeState() {
        throw new UnsupportedOperationException("This class is not meant to be instantiated");
    }

    static void snapshot(MavenSession session, Path root) {
        session.getRepositorySession().getData().set(KEY, TreeFingerprint.from(root));
    }

    static boolean unchanged(MavenSession session, Path root) {
        SessionData data = session.getRepositorySession().getData();
        String initial = Optional.ofNullable(data.get(KEY)).map(String.class::cast).orElseThrow(
            () -> new IllegalStateException("The Airness tree snapshot did not run in this Maven session")
        );
        return initial.equals(TreeFingerprint.from(root));
    }
}
