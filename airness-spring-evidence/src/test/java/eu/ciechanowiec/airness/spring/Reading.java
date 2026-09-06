package eu.ciechanowiec.airness.spring;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * A type whose guard reads a property of a bean rather than calling a method of it. The bean is still
 * resolved, and the member is not, because a property is reached through a name the engine derives
 * rather than through the one the expression wrote.
 */
final class Reading {

    @PreAuthorize("@clearance.open")
    String reached(String reference) {
        return reference.strip();
    }
}
