package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Stable Docker tags are compared only within an exact syntactic family, preventing unrelated release
 * channels from being presented as upgrades.
 */
class DockerTagTest {

    @Test
    void comparesNumericTagsWithinTheSameFamily() {
        DockerTag current = DockerTag.from("v8.30.0").orElseThrow();
        DockerTag latest = DockerTag.from("v8.31.0").orElseThrow();

        assertTrue(current.sameScheme(latest));
        assertTrue(current.compareVersion(latest) < 0);
    }

    @Test
    void separatesPrefixesComponentCountsAndCalendarVersions() {
        DockerTag semantic = DockerTag.from("8.30.0").orElseThrow();

        assertFalse(semantic.sameScheme(DockerTag.from("v8.31.0").orElseThrow()));
        assertFalse(semantic.sameScheme(DockerTag.from("8.31").orElseThrow()));
        assertFalse(semantic.sameScheme(DockerTag.from("2027.1.0").orElseThrow()));
    }

    @Test
    void excludesAliasesAndPrereleases() {
        assertTrue(DockerTag.from("latest").isEmpty());
        assertTrue(DockerTag.from("1.2.3-rc1").isEmpty());
        assertTrue(DockerTag.from("2027").isEmpty());
    }
}
