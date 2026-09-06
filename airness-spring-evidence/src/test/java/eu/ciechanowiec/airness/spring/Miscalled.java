package eu.ciechanowiec.airness.spring;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * A type whose guard names a bean the application does declare and calls a method that bean does not
 * answer to, which is the second of the two ways a guard fails to resolve.
 */
final class Miscalled {

    @PreAuthorize("@clearance.grantd(#reference)")
    String reached(String reference) {
        return reference.strip();
    }
}
