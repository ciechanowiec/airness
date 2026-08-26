package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * Docker references retain their original spelling for replacement advice while exposing Docker Hub
 * repository coordinates to the registry client.
 */
class DockerReferenceTest {

    private static final String DIGEST = "sha256:" + "a".repeat(64);

    @Test
    void acceptsDockerHubHostAliases() {
        DockerReference reference = DockerReference.from(
            new DeclaredContainerImage(
                "scanner.image", "registry-1.docker.io/example/scanner:1.2.3@" + DIGEST
            )
        );

        assertEquals("example", reference.namespace());
        assertEquals("scanner", reference.repository());
        assertEquals("registry-1.docker.io/example/scanner:1.2.3", reference.tagged());
        assertEquals(
            "registry-1.docker.io/example/scanner:2.0.0@" + DIGEST,
            reference.pinned("2.0.0", DIGEST)
        );
    }

    @Test
    void acceptsTheDockerIoAlias() {
        DockerReference reference = DockerReference.from(
            new DeclaredContainerImage("scanner.image", "docker.io/example/scanner:1.2@" + DIGEST)
        );
        assertEquals("example/scanner", reference.namespace() + '/' + reference.repository());
    }

    @Test
    void rejectsAnUnpinnedOrUnnamespacedImage() {
        assertThrows(
            IllegalArgumentException.class,
            () -> DockerReference.from(new DeclaredContainerImage("scanner.image", "scanner:1.2"))
        );
        assertThrows(
            IllegalArgumentException.class,
            () -> DockerReference.from(
                new DeclaredContainerImage("scanner.image", "scanner:1.2@" + DIGEST)
            )
        );
    }
}
