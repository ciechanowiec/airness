package eu.ciechanowiec.airness.maven;

import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

/**
 * Whether a Maven version names a SNAPSHOT, a moving build that is never published as a release.
 */
@UtilityClass
final class SnapshotVersions {

    private static final String SNAPSHOT = "-SNAPSHOT";

    /**
     * Whether a version is a SNAPSHOT.
     *
     * @param version the version, which reads as no SNAPSHOT when absent
     * @return whether it ends with the SNAPSHOT qualifier
     */
    static boolean snapshot(@Nullable String version) {
        return Optional.ofNullable(version).orElse("").endsWith(SNAPSHOT);
    }
}
