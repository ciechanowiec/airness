package eu.ciechanowiec.airness.spring;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * A type whose guard names a bean the application declares and calls a method that bean answers to,
 * which is the shape nothing is reported about.
 */
final class Delegating {

    @PreAuthorize("@clearance.granted(#reference)")
    String reached(String reference) {
        return reference.strip();
    }
}
