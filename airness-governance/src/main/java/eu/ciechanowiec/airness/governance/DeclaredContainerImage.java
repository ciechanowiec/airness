package eu.ciechanowiec.airness.governance;

import java.util.Objects;

/**
 * A digest-pinned container image and the property that declares it.
 *
 * @param property  the owning property
 * @param reference the {@code repository:tag@sha256:digest} reference
 */
public record DeclaredContainerImage(String property, String reference) {

    /**
     * Rejects a declaration that cannot identify its property or image.
     */
    public DeclaredContainerImage {
        Objects.requireNonNull(property);
        Objects.requireNonNull(reference);
        if (property.isBlank() || reference.isBlank()) {
            throw new IllegalArgumentException("A container image declaration must name its property and reference");
        }
    }
}
