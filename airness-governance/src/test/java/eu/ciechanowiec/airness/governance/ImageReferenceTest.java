package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * A reference is read into one repository spelling however the file wrote it, and the tag and digest
 * are told apart from a registry port.
 */
class ImageReferenceTest {

    private static final String DIGEST = "sha256:" + "a".repeat(64);

    @Test
    void normalisesEverySpellingOfAnOfficialImage() {
        List<String> spellings = List.of(
            "mongo", "library/mongo", "docker.io/mongo", "docker.io/library/mongo", "index.docker.io/library/mongo:7"
        );
        assertTrue(
            spellings.stream().map(ImageReference::parse).map(ImageReference::repository).allMatch("mongo"::equals),
            "five spellings of the official image are one repository"
        );
    }

    @Test
    void keepsAForeignRegistryHostAndDropsTheDockerHubOne() {
        assertEquals("quay.io/minio/minio", ImageReference.parse("quay.io/minio/minio:latest").repository());
        assertEquals("bitnami/redis", ImageReference.parse("docker.io/bitnami/redis").repository());
        assertEquals("mongo", ImageReference.parse("MONGO").repository(), "case is not part of a repository name");
    }

    @Test
    void separatesTheTagFromARegistryPort() {
        ImageReference reference = ImageReference.parse("localhost:5000/team/app:1.2.3");
        assertEquals("localhost:5000/team/app", reference.repository(), "the port belongs to the host");
        assertEquals(
            Optional.of("1.2.3"), reference.tag(), "and the tag is what follows the last colon after the path"
        );
    }

    @Test
    void readsADigestBesideOrInsteadOfATag() {
        assertEquals(Optional.of(DIGEST), ImageReference.parse("postgres:18@" + DIGEST).digest());
        assertEquals(
            Optional.empty(), ImageReference.parse("postgres@" + DIGEST).tag(), "a digest alone leaves no tag"
        );
    }

    @Test
    void isMutableWithoutATagOrWithTheLatestTag() {
        assertTrue(ImageReference.parse("postgres").mutable(), "no tag pulls whatever is newest");
        assertTrue(ImageReference.parse("dpage/pgadmin4:latest").mutable(), "and so does the latest tag");
        assertFalse(ImageReference.parse("postgres:18").mutable(), "a tag pins");
        assertFalse(ImageReference.parse("postgres@" + DIGEST).mutable(), "and a digest pins without one");
    }

    @Test
    void reportsAVariableNothingSubstituted() {
        assertTrue(ImageReference.parse("redis:${REDIS_TAG}").unresolved(), "a variable leaves the tag unknown");
        assertFalse(ImageReference.parse("redis:7.2.4").unresolved(), "a literal reference is resolved");
    }
}
