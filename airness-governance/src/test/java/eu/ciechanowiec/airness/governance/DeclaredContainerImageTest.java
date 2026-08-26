package eu.ciechanowiec.airness.governance;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * A declared container image always identifies both its owning property and reference.
 */
class DeclaredContainerImageTest {

    @Test
    void rejectsBlankParts() {
        assertThrows(IllegalArgumentException.class, () -> new DeclaredContainerImage(" ", "image"));
        assertThrows(IllegalArgumentException.class, () -> new DeclaredContainerImage("image", " "));
    }
}
